package com.ispf.server.workflow;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.Variable;
import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.MessageTaskDefinition;
import com.ispf.plugin.workflow.ServiceTaskDefinition;
import com.ispf.plugin.workflow.UserTaskDefinition;
import com.ispf.plugin.workflow.WorkflowActionType;
import com.ispf.plugin.workflow.WorkflowConditionEvaluator;
import com.ispf.plugin.workflow.WorkflowEngine;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.function.FunctionService;
import com.ispf.server.event.EventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkflowService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private static final DataSchema STRING_VALUE = DataSchema.builder("stringValue")
            .field("value", FieldType.STRING)
            .build();

    private final ObjectManager objectManager;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;
    private final WorkflowEngine workflowEngine;
    private final NatsEventBridge natsEventBridge;
    private final ObjectMapper objectMapper;
    private final WorkflowInstanceStore instanceStore;
    private final WorkflowConditionFactory conditionFactory;
    private final FunctionService functionService;
    private final EventService eventService;
    private final WorkQueueService workQueueService;
    private final WorkflowInstanceRepository instanceRepository;

    public WorkflowService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine,
            WorkflowEngine workflowEngine,
            NatsEventBridge natsEventBridge,
            ObjectMapper objectMapper,
            WorkflowInstanceStore instanceStore,
            WorkflowConditionFactory conditionFactory,
            FunctionService functionService,
            EventService eventService,
            @Lazy WorkQueueService workQueueService,
            WorkflowInstanceRepository instanceRepository
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
        this.workflowEngine = workflowEngine;
        this.natsEventBridge = natsEventBridge;
        this.objectMapper = objectMapper;
        this.instanceStore = instanceStore;
        this.conditionFactory = conditionFactory;
        this.functionService = functionService;
        this.eventService = eventService;
        this.workQueueService = workQueueService;
        this.instanceRepository = instanceRepository;
    }

    @Transactional
    public void ensureWorkflowStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.WORKFLOW) {
            throw new IllegalArgumentException("Not a workflow object: " + path);
        }
        if (node.getVariable("bpmnXml").isPresent()) {
            return;
        }
        modelRegistry.findByName("workflow-v1").ifPresent(model -> {
            modelEngine.applyModel(model.id(), path);
            objectManager.persistNodeTree(path);
        });
    }

    public WorkflowView getWorkflow(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.type() != ObjectType.WORKFLOW) {
            throw new IllegalArgumentException("Not a workflow object: " + path);
        }
        return new WorkflowView(
                path,
                readString(node, "title").orElse(node.displayName()),
                readLifecycleStatus(node),
                readString(node, "bpmnXml").orElse(""),
                readString(node, "triggerJson").orElse("{}"),
                readString(node, "instanceState").orElse("{}"),
                readString(node, "lastRunAt").orElse(null)
        );
    }

    @Transactional
    public WorkflowView saveBpmn(String path, String bpmnXml) throws WorkflowException {
        workflowEngine.parse(bpmnXml);
        objectManager.setVariableValue(
                path,
                "bpmnXml",
                DataRecord.single(STRING_VALUE, Map.of("value", bpmnXml))
        );
        return getWorkflow(path);
    }

    @Transactional
    public WorkflowView updateStatus(String path, WorkflowLifecycleStatus status) {
        objectManager.setVariableValue(
                path,
                "status",
                DataRecord.single(STRING_VALUE, Map.of("value", status.name()))
        );
        return getWorkflow(path);
    }

    @Transactional
    public WorkflowView runWorkflow(String path) throws WorkflowException {
        return runWorkflow(path, null);
    }

    @Transactional
    public WorkflowView runWorkflow(String path, String triggerObjectPath) throws WorkflowException {
        PlatformObject node = objectManager.require(path);
        String bpmnXml = readString(node, "bpmnXml").orElseThrow(() ->
                new WorkflowException("Workflow BPMN is empty: " + path));
        BpmnProcess process = workflowEngine.parse(bpmnXml);
        WorkflowInstance instance = workflowEngine.start(path, process);
        if (triggerObjectPath != null) {
            instance.setVariable("triggerObjectPath", triggerObjectPath);
        }

        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(triggerObjectPath);
        while (instance.status() == InstanceStatus.RUNNING) {
            workflowEngine.step(instance, process, this::executeTask, this::executeMessageTask, evaluator);
        }

        UserTaskDefinition pendingTask = instance.pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(instance, process, triggerObjectPath, pendingTask);
        persistInstanceSnapshot(path, instance);
        publishInstanceEvent(path, instance);
        return getWorkflow(path);
    }

    @Transactional
    public void claimInstance(String instanceId, String operatorId) throws WorkflowException {
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        stored.instance().claim(operatorId);
        String bpmnXml = readString(objectManager.require(stored.instance().workflowPath()), "bpmnXml")
                .orElseThrow(() -> new WorkflowException("BPMN missing"));
        BpmnProcess process = workflowEngine.parse(bpmnXml);
        UserTaskDefinition pendingTask = stored.instance().pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(stored.instance(), process, stored.triggerObjectPath(), pendingTask);
    }

    @Transactional
    public WorkflowView completeUserTask(String instanceId, String operatorId) throws WorkflowException {
        return completeUserTask(instanceId, null, operatorId);
    }

    @Transactional
    public WorkflowView completeUserTask(String instanceId, String taskNodeId, String operatorId) throws WorkflowException {
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        WorkflowInstance instance = stored.instance();
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting: " + instanceId);
        }

        String workflowPath = instance.workflowPath();
        BpmnProcess process = workflowEngine.parse(
                readString(objectManager.require(workflowPath), "bpmnXml")
                        .orElseThrow(() -> new WorkflowException("BPMN missing"))
        );

        String userTaskId = taskNodeId != null && !taskNodeId.isBlank()
                ? taskNodeId
                : instance.pendingUserTaskId()
                        .orElseThrow(() -> new WorkflowException("No pending user task"));
        UserTaskDefinition userTask = process.userTasks().get(userTaskId);
        if (userTask != null) {
            executeUserTaskAction(userTask, stored.triggerObjectPath());
        }

        instance.claim(operatorId);
        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(stored.triggerObjectPath());
        workflowEngine.completeUserTask(instance, process, userTaskId, this::executeTask, this::executeMessageTask, evaluator);

        UserTaskDefinition nextPending = instance.pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(instance, process, stored.triggerObjectPath(), nextPending);
        persistInstanceSnapshot(workflowPath, instance);
        publishInstanceEvent(workflowPath, instance);
        return getWorkflow(workflowPath);
    }

    @Transactional
    public Map<String, Object> deliverSignal(String instanceId, String signalName, String operatorId)
            throws WorkflowException {
        if (signalName == null || signalName.isBlank()) {
            throw new WorkflowException("Signal name is required");
        }
        WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(instanceId);
        WorkflowInstance instance = stored.instance();
        if (instance.status() != InstanceStatus.WAITING) {
            throw new WorkflowException("Instance is not waiting: " + instanceId);
        }
        if (!instance.pendingSignalNames().contains(signalName)) {
            throw new WorkflowException("Instance is not waiting for signal: " + signalName);
        }

        String workflowPath = instance.workflowPath();
        BpmnProcess process = workflowEngine.parse(
                readString(objectManager.require(workflowPath), "bpmnXml")
                        .orElseThrow(() -> new WorkflowException("BPMN missing"))
        );

        if (operatorId != null && !operatorId.isBlank()) {
            instance.claim(operatorId);
        }
        WorkflowConditionEvaluator evaluator = conditionFactory.forTriggerObjectPath(stored.triggerObjectPath());
        workflowEngine.deliverSignal(
                instance,
                process,
                signalName,
                this::executeTask,
                this::executeMessageTask,
                evaluator
        );

        UserTaskDefinition nextPending = instance.pendingUserTaskId()
                .map(id -> process.userTasks().get(id))
                .orElse(null);
        instanceStore.save(instance, process, stored.triggerObjectPath(), nextPending);
        persistInstanceSnapshot(workflowPath, instance);
        publishInstanceEvent(workflowPath, instance);

        return Map.of(
                "instanceId", instanceId,
                "signal", signalName,
                "status", instance.status().name()
        );
    }

    @Transactional
    public Map<String, Object> deliverSignalByWorkflowPath(String workflowPath, String signalName, String operatorId)
            throws WorkflowException {
        if (signalName == null || signalName.isBlank()) {
            throw new WorkflowException("Signal name is required");
        }
        List<WorkflowInstanceEntity> waiting = instanceRepository.findByWorkflowPathAndStatus(
                workflowPath,
                InstanceStatus.WAITING.name()
        );
        List<String> signaled = new ArrayList<>();
        for (WorkflowInstanceEntity entity : waiting) {
            WorkflowInstanceStore.StoredWorkflowInstance stored = instanceStore.load(entity.getId());
            if (!stored.instance().pendingSignalNames().contains(signalName)) {
                continue;
            }
            deliverSignal(entity.getId(), signalName, operatorId);
            signaled.add(entity.getId());
        }
        return Map.of(
                "workflowPath", workflowPath,
                "signal", signalName,
                "signaledCount", signaled.size(),
                "instanceIds", signaled
        );
    }

    @Transactional
    public void handleVariableTrigger(String objectPath, String variableName) {
        for (PlatformObject node : objectManager.tree().all()) {
            if (node.type() != ObjectType.WORKFLOW) {
                continue;
            }
            if (readLifecycleStatus(node) != WorkflowLifecycleStatus.ACTIVE) {
                continue;
            }
            if (!matchesTrigger(node, objectPath, variableName)) {
                continue;
            }
            if (!isTriggerConditionMet(node, objectPath, variableName)) {
                continue;
            }
            try {
                runWorkflow(node.path(), objectPath);
            } catch (WorkflowException e) {
                log.warn("Workflow trigger failed for {}: {}", node.path(), e.getMessage());
            }
        }
    }

    private void executeUserTaskAction(UserTaskDefinition userTask, String triggerObjectPath) {
        Map<String, String> params = userTask.parameters();
        String functionName = params.get("function");
        String targetObject = params.getOrDefault("targetObject", triggerObjectPath);
        if (functionName == null || functionName.isBlank() || targetObject == null || targetObject.isBlank()) {
            return;
        }
        try {
            functionService.invoke(targetObject, functionName);
        } catch (Exception e) {
            log.warn("User task function {} on {} failed: {}", functionName, targetObject, e.getMessage());
        }
    }

    private void executeMessageTask(MessageTaskDefinition task, WorkflowInstance instance) {
        if ("nats".equalsIgnoreCase(task.channel())) {
            natsEventBridge.publish(task.subject(), task.message());
            return;
        }
        log.info("[workflow:{}] message {} -> {}", instance.workflowPath(), task.subject(), task.message());
    }

    private void executeTask(ServiceTaskDefinition task, WorkflowInstance instance) throws WorkflowException {
        Map<String, String> params = task.parameters();
        switch (task.action()) {
            case LOG -> log.info("[workflow:{}] {}", instance.workflowPath(), params.getOrDefault("message", task.name()));
            case SET_VARIABLE -> {
                String target = required(params, "targetObject");
                String variable = required(params, "variable");
                String value = params.getOrDefault("value", "");
                objectManager.setVariableValue(
                        target,
                        variable,
                        DataRecord.single(STRING_VALUE, Map.of("value", value))
                );
            }
            case PUBLISH_NATS -> natsEventBridge.publish(
                    params.getOrDefault("subject", "ispf.workflow.event"),
                    params.getOrDefault("message", task.name())
            );
            case INVOKE_FUNCTION -> invokeWorkflowFunction(params, instance);
            case FIRE_EVENT -> {
                String target = params.getOrDefault("objectPath", params.getOrDefault("targetObject", ""));
                String eventName = required(params, "eventName");
                DataRecord payload = resolveEventPayload(target, params.get("payloadVariable"));
                eventService.fire(target, eventName, payload);
            }
            case READ_VARIABLE -> {
                String target = params.getOrDefault("objectPath", params.getOrDefault("targetObject", ""));
                String variable = params.get("sourceVariable");
                if (variable == null || variable.isBlank()) {
                    variable = required(params, "variable");
                }
                String valueField = params.getOrDefault("valueField", "value");
                String contextKey = params.getOrDefault("contextKey", variable);
                String value = readObjectVariableField(target, variable, valueField);
                instance.setVariable(contextKey, value);
            }
            case START_WORKFLOW -> {
                String childPath = required(params, "workflowPath");
                runWorkflow(childPath, params.get("objectPath"));
            }
        }
    }

    private DataRecord resolveEventPayload(String objectPath, String payloadVariable) {
        if (payloadVariable == null || payloadVariable.isBlank()) {
            return null;
        }
        PlatformObject node = objectManager.require(objectPath);
        return node.getVariable(payloadVariable)
                .flatMap(Variable::value)
                .orElse(null);
    }

    private String readObjectVariableField(String objectPath, String variableName, String valueField) {
        PlatformObject node = objectManager.require(objectPath);
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> {
                    Object value = record.firstRow().get(valueField);
                    return value != null ? String.valueOf(value) : "";
                })
                .orElse("");
    }

    private void invokeWorkflowFunction(Map<String, String> params, WorkflowInstance instance) throws WorkflowException {
        String objectPath = required(params, "objectPath");
        String functionName = required(params, "functionName");
        String inputMap = params.getOrDefault("inputMap", "");
        DataRecord input = buildWorkflowFunctionInput(inputMap, instance);
        DataRecord output = functionService.invoke(objectPath, functionName, input);
        applyWorkflowFunctionOutput(params.get("outputMap"), output, instance);
        if (output != null && output.rowCount() > 0) {
            Object errorCode = output.firstRow().get("error_code");
            if (errorCode != null && !"OK".equals(String.valueOf(errorCode))) {
                throw new WorkflowException("Function " + functionName + " failed: " + errorCode);
            }
        }
    }

    private DataRecord buildWorkflowFunctionInput(String inputMap, WorkflowInstance instance) {
        if (inputMap == null || inputMap.isBlank()) {
            return null;
        }
        Map<String, Object> row = new HashMap<>();
        DataSchema.Builder schemaBuilder = DataSchema.builder("workflowFunctionInput");
        for (String part : inputMap.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String key = kv[0].trim();
            String valueExpr = kv[1].trim();
            String value = valueExpr.startsWith("${") && valueExpr.endsWith("}")
                    ? instance.variables().getOrDefault(valueExpr.substring(2, valueExpr.length() - 1), "")
                    : valueExpr;
            row.put(key, value);
            schemaBuilder.field(key, FieldType.STRING);
        }
        return row.isEmpty() ? null : DataRecord.single(schemaBuilder.build(), row);
    }

    private void applyWorkflowFunctionOutput(String outputMap, DataRecord output, WorkflowInstance instance) {
        if (outputMap == null || outputMap.isBlank() || output == null || output.rowCount() == 0) {
            return;
        }
        Map<String, Object> resultRow = output.firstRow();
        for (String part : outputMap.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            String workflowVar = kv[0].trim();
            String resultField = kv[1].trim();
            instance.setVariable(workflowVar, String.valueOf(resultRow.get(resultField)));
        }
    }

    private void publishInstanceEvent(String path, WorkflowInstance instance) {
        if (instance.status() == InstanceStatus.COMPLETED) {
            natsEventBridge.publishWorkflowEvent(path, "completed", Map.of(
                    "instanceId", instance.instanceId(),
                    "status", instance.status().name()
            ));
        } else if (instance.status() == InstanceStatus.WAITING) {
            natsEventBridge.publishWorkflowEvent(path, "waiting", Map.of(
                    "instanceId", instance.instanceId(),
                    "taskId", instance.pendingUserTaskId().orElse(""),
                    "signal", instance.pendingSignalName().orElse("")
            ));
        }
    }

    private void persistInstanceSnapshot(String path, WorkflowInstance instance) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("instanceId", instance.instanceId());
            state.put("status", instance.status().name());
            state.put("currentNodeId", instance.currentNodeId());
            state.put("startedAt", instance.startedAt().toString());
            state.put("completedAt", instance.completedAt() != null ? instance.completedAt().toString() : null);
            state.put("history", instance.history());
            state.put("errorMessage", instance.errorMessage());
            state.put("assignee", instance.assignee().orElse(null));
            state.put("pendingUserTaskId", instance.pendingUserTaskId().orElse(null));
            state.put("pendingSignalName", instance.pendingSignalName().orElse(null));

            String json = objectMapper.writeValueAsString(state);
            objectManager.setVariableValue(path, "instanceState", DataRecord.single(STRING_VALUE, Map.of("value", json)));
            objectManager.setVariableValue(
                    path,
                    "lastRunAt",
                    DataRecord.single(STRING_VALUE, Map.of("value", Instant.now().toString()))
            );
            objectManager.persistNodeTree(path);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist workflow instance state", e);
        }
    }

    private boolean matchesTrigger(PlatformObject workflow, String objectPath, String variableName) {
        try {
            String triggerJson = readString(workflow, "triggerJson").orElse("{}");
            JsonNode trigger = objectMapper.readTree(triggerJson);
            return objectPath.equals(trigger.path("objectPath").asText())
                    && variableName.equals(trigger.path("variableName").asText());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTriggerConditionMet(PlatformObject workflow, String objectPath, String variableName) {
        try {
            String triggerJson = readString(workflow, "triggerJson").orElse("{}");
            JsonNode trigger = objectMapper.readTree(triggerJson);
            String valueField = trigger.path("valueField").asText("value");
            JsonNode expected = trigger.get("expectedValue");
            if (expected == null || expected.isNull()) {
                return true;
            }

            PlatformObject source = objectManager.require(objectPath);
            Optional<Variable> variable = source.getVariable(variableName);
            if (variable.isEmpty() || variable.get().value().isEmpty()) {
                return false;
            }
            Object actual = variable.get().value().get().firstRow().get(valueField);
            if (expected.isBoolean()) {
                return Boolean.TRUE.equals(actual) || "true".equals(String.valueOf(actual));
            }
            return expected.asText().equals(String.valueOf(actual));
        } catch (Exception e) {
            log.debug("Trigger condition check failed for {}: {}", workflow.path(), e.getMessage());
            return false;
        }
    }

    private static String required(Map<String, String> params, String key) throws WorkflowException {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new WorkflowException("Missing service task parameter: " + key);
        }
        return value;
    }

    private static WorkflowLifecycleStatus readLifecycleStatus(PlatformObject node) {
        return readString(node, "status")
                .map(WorkflowLifecycleStatus::valueOf)
                .orElse(WorkflowLifecycleStatus.DRAFT);
    }

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank());
    }

    public record WorkflowView(
            String path,
            String title,
            WorkflowLifecycleStatus status,
            String bpmnXml,
            String triggerJson,
            String instanceState,
            String lastRunAt
    ) {
    }
}
