package com.ispf.server.workflow;

import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.workflow.BpmnProcess;
import com.ispf.plugin.workflow.ExecutionToken;
import com.ispf.plugin.workflow.InstanceStatus;
import com.ispf.plugin.workflow.UserTaskDefinition;
import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.plugin.workflow.WorkflowInstance;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.WorkflowUserTaskRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import com.ispf.server.persistence.entity.WorkflowUserTaskEntity;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkflowInstanceStore {

    private final WorkflowInstanceRepository instanceRepository;
    private final WorkflowUserTaskRepository userTaskRepository;
    private final ObjectManager objectManager;
    private final ObjectMapper objectMapper;

    public WorkflowInstanceStore(
            WorkflowInstanceRepository instanceRepository,
            WorkflowUserTaskRepository userTaskRepository,
            ObjectManager objectManager,
            ObjectMapper objectMapper
    ) {
        this.instanceRepository = instanceRepository;
        this.userTaskRepository = userTaskRepository;
        this.objectManager = objectManager;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void save(
            WorkflowInstance instance,
            BpmnProcess process,
            String triggerObjectPath,
            UserTaskDefinition pendingUserTask
    ) {
        WorkflowInstanceEntity entity = instanceRepository.findById(instance.instanceId())
                .orElseGet(WorkflowInstanceEntity::new);
        entity.setId(instance.instanceId());
        entity.setWorkflowPath(instance.workflowPath());
        entity.setStatus(instance.status().name());
        entity.setCurrentNodeId(instance.currentNodeId());
        entity.setAssignee(instance.assignee().orElse(null));
        entity.setTriggerObjectPath(triggerObjectPath);
        entity.setStateJson(serialize(instance, process));
        entity.setStartedAt(instance.startedAt());
        entity.setCompletedAt(instance.completedAt());
        entity.setUpdatedAt(Instant.now());
        instanceRepository.save(entity);

        if (instance.status() == InstanceStatus.WAITING && pendingUserTask != null) {
            syncUserTasks(instance, process, pendingUserTask);
        } else if (instance.status() == InstanceStatus.WAITING) {
            syncAllWaitingUserTasks(instance, process);
        }
    }

    private void syncAllWaitingUserTasks(WorkflowInstance instance, BpmnProcess process) {
        for (String taskNodeId : instance.pendingUserTaskIds()) {
            UserTaskDefinition definition = process.userTasks().get(taskNodeId);
            if (definition != null) {
                upsertOpenUserTask(instance, definition);
            }
        }
    }

    private void syncUserTasks(WorkflowInstance instance, BpmnProcess process, UserTaskDefinition pendingUserTask) {
        if (instance.pendingUserTaskIds().size() > 1) {
            syncAllWaitingUserTasks(instance, process);
            return;
        }
        upsertOpenUserTask(instance, pendingUserTask);
    }

    private void upsertOpenUserTask(WorkflowInstance instance, UserTaskDefinition pendingUserTask) {
            WorkflowUserTaskEntity task = userTaskRepository
                    .findByInstanceIdAndTaskNodeIdAndStatus(instance.instanceId(), pendingUserTask.id(), "OPEN")
                    .or(() -> userTaskRepository.findByInstanceIdAndTaskNodeIdAndStatus(
                            instance.instanceId(), pendingUserTask.id(), "CLAIMED"))
                    .orElseGet(WorkflowUserTaskEntity::new);
            if (task.getId() == null) {
                task.setId(UUID.randomUUID().toString());
                task.setCreatedAt(Instant.now());
            }
            task.setInstanceId(instance.instanceId());
            task.setWorkflowPath(instance.workflowPath());
            task.setOperatorAppId(resolveOperatorAppId(instance.workflowPath()));
            task.setTaskNodeId(pendingUserTask.id());
            task.setTitle(pendingUserTask.title());
            task.setInstructions(pendingUserTask.instructions());
            task.setAssigneeRole(pendingUserTask.assigneeRole());
            task.setStatus(task.getStatus() == null ? "OPEN" : task.getStatus());
            if (task.getStatus() == null || task.getStatus().isBlank()) {
                task.setStatus("OPEN");
            }
            userTaskRepository.save(task);
    }

    private String resolveOperatorAppId(String workflowPath) {
        try {
            PlatformObject workflow = objectManager.require(workflowPath);
            return readString(workflow, "operatorAppId").orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Optional<String> readString(PlatformObject node, String variableName) {
        return node.getVariable(variableName)
                .flatMap(Variable::value)
                .map(record -> record.firstRow().get("value"))
                .map(Object::toString)
                .filter(value -> !value.isBlank());
    }

    @Transactional(readOnly = true)
    public StoredWorkflowInstance load(String instanceId) throws WorkflowException {
        WorkflowInstanceEntity entity = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new WorkflowException("Workflow instance not found: " + instanceId));
        return deserialize(entity);
    }

    private String serialize(WorkflowInstance instance, BpmnProcess process) {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("processId", process.id());
            state.put("variables", instance.variables());
            state.put("history", instance.history());
            state.put("errorMessage", instance.errorMessage());
            state.put("pendingUserTaskId", instance.pendingUserTaskId().orElse(null));
            state.putAll(instance.serializeTokens());
            return objectMapper.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize workflow instance", e);
        }
    }

    @SuppressWarnings("unchecked")
    private StoredWorkflowInstance deserialize(WorkflowInstanceEntity entity) throws WorkflowException {
        try {
            Map<String, Object> state = objectMapper.readValue(entity.getStateJson(), Map.class);
            List<String> history = (List<String>) state.getOrDefault("history", List.of());
            Map<String, String> variables = (Map<String, String>) state.getOrDefault("variables", Map.of());
            String pendingUserTaskId = (String) state.get("pendingUserTaskId");
            String errorMessage = (String) state.get("errorMessage");
            List<ExecutionToken> tokens = WorkflowInstance.deserializeTokens(state);

            WorkflowInstance instance = WorkflowInstance.restore(
                    entity.getId(),
                    entity.getWorkflowPath(),
                    InstanceStatus.valueOf(entity.getStatus()),
                    entity.getCurrentNodeId(),
                    entity.getStartedAt(),
                    entity.getCompletedAt(),
                    entity.getAssignee(),
                    pendingUserTaskId,
                    history,
                    variables,
                    errorMessage,
                    tokens
            );
            return new StoredWorkflowInstance(instance, entity.getTriggerObjectPath(), state);
        } catch (Exception e) {
            throw new WorkflowException("Failed to deserialize workflow instance", e);
        }
    }

    public record StoredWorkflowInstance(
            WorkflowInstance instance,
            String triggerObjectPath,
            Map<String, Object> state
    ) {
    }
}
