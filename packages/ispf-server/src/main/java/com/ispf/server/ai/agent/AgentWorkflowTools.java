package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.persistence.WorkflowInstanceRepository;
import com.ispf.server.persistence.entity.WorkflowInstanceEntity;
import com.ispf.server.security.acl.ObjectAccessService;
import com.ispf.server.tenant.TenantScopeService;
import com.ispf.server.workflow.WorkflowInstanceCancelService;
import com.ispf.server.workflow.WorkflowService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent tools for BPMN workflows on tree WORKFLOW objects.
 */
final class AgentWorkflowTools {

    private AgentWorkflowTools() {
    }

    static List<PlatformAgentTool> all(
            WorkflowService workflowService,
            WorkflowInstanceCancelService cancelService,
            WorkflowInstanceRepository instanceRepository,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return List.of(
                getWorkflowTool(workflowService, objectAccessService, tenantScopeService),
                saveWorkflowBpmnTool(workflowService, objectManager, objectAccessService, tenantScopeService),
                runWorkflowTool(workflowService, objectManager, objectAccessService, tenantScopeService),
                updateWorkflowStatusTool(workflowService, objectManager, objectAccessService, tenantScopeService),
                listWorkflowInstancesTool(
                        workflowService,
                        instanceRepository,
                        objectManager,
                        objectAccessService,
                        tenantScopeService,
                        objectMapper
                ),
                cancelWorkflowInstanceTool(cancelService, objectAccessService, tenantScopeService),
                signalWorkflowInstanceTool(workflowService, objectAccessService, tenantScopeService)
        );
    }

    private static PlatformAgentTool getWorkflowTool(
            WorkflowService workflowService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_workflow";
            }

            @Override
            public String description() {
                return "Read WORKFLOW object: title, status, bpmnXml, triggerJson, operatorAppId, lastRunAt, instanceState. "
                        + "Arg: path.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                return readWorkflow(path, context, workflowService, objectAccessService, tenantScopeService);
            }
        };
    }

    private static PlatformAgentTool saveWorkflowBpmnTool(
            WorkflowService workflowService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "save_workflow_bpmn";
            }

            @Override
            public String description() {
                return "Save BPMN 2.0 XML on a WORKFLOW object. Args: path, bpmnXml (string). "
                        + "Use get_automation_schema topic=workflow for structure hints.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String bpmnXml = stringArg(arguments, "bpmnXml");
                if (path.isBlank() || bpmnXml.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path and bpmnXml are required");
                }
                var auth = context.authentication();
                if (!requireWorkflowWrite(path, auth, objectManager, objectAccessService, tenantScopeService)) {
                    return Map.of("status", "ERROR", "error", "Not a writable WORKFLOW: " + path);
                }
                try {
                    WorkflowService.WorkflowView saved = workflowService.saveBpmn(path, bpmnXml);
                    return Map.of("status", "OK", "workflow", workflowSummary(saved));
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool runWorkflowTool(
            WorkflowService workflowService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "run_workflow";
            }

            @Override
            public String description() {
                return "Start workflow instance from BPMN. Args: path, optional triggerObjectPath.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!requireWorkflowWrite(path, auth, objectManager, objectAccessService, tenantScopeService)) {
                    return Map.of("status", "ERROR", "error", "Not a writable WORKFLOW: " + path);
                }
                try {
                    String trigger = stringArg(arguments, "triggerObjectPath");
                    WorkflowService.WorkflowView result = trigger.isBlank()
                            ? workflowService.runWorkflow(path)
                            : workflowService.runWorkflow(path, trigger);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("status", "OK");
                    response.put("workflow", workflowSummary(result));
                    response.put("instanceState", result.instanceState());
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool updateWorkflowStatusTool(
            WorkflowService workflowService,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "update_workflow_status";
            }

            @Override
            public String description() {
                return "Set workflow lifecycle status. Args: path, status (ACTIVE|INACTIVE|DRAFT).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                String status = stringArg(arguments, "status");
                if (path.isBlank() || status.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path and status are required");
                }
                var auth = context.authentication();
                if (!requireWorkflowWrite(path, auth, objectManager, objectAccessService, tenantScopeService)) {
                    return Map.of("status", "ERROR", "error", "Not a writable WORKFLOW: " + path);
                }
                try {
                    WorkflowLifecycleStatus lifecycle = WorkflowLifecycleStatus.valueOf(status.trim().toUpperCase());
                    WorkflowService.WorkflowView saved = workflowService.updateStatus(path, lifecycle);
                    return Map.of("status", "OK", "workflow", workflowSummary(saved));
                } catch (IllegalArgumentException ex) {
                    return Map.of("status", "ERROR", "error", "Invalid status: " + status);
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool listWorkflowInstancesTool(
            WorkflowService workflowService,
            WorkflowInstanceRepository instanceRepository,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_workflow_instances";
            }

            @Override
            public String description() {
                return "List recent workflow instances for a WORKFLOW path. Args: path, optional limit (default 10).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String path = stringArg(arguments, "path");
                if (path.isBlank()) {
                    return Map.of("status", "ERROR", "error", "path is required");
                }
                var auth = context.authentication();
                if (!tenantScopeService.isPathVisible(path, auth)) {
                    return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
                }
                objectAccessService.requireRead(path, auth);
                try {
                    objectManager.require(path);
                    int limit = intArg(arguments, "limit", 10);
                    List<Map<String, Object>> instances = new ArrayList<>();
                    instanceRepository.findFirstByWorkflowPathOrderByStartedAtDesc(path)
                            .ifPresent(entity -> instances.add(instanceRow(entity)));
                    for (String status : List.of("RUNNING", "WAITING", "FAILED")) {
                        for (WorkflowInstanceEntity entity : instanceRepository.findByWorkflowPathAndStatus(path, status)) {
                            if (instances.stream().noneMatch(row -> entity.getId().equals(row.get("instanceId")))) {
                                instances.add(instanceRow(entity));
                            }
                            if (instances.size() >= limit) {
                                break;
                            }
                        }
                        if (instances.size() >= limit) {
                            break;
                        }
                    }
                    WorkflowService.WorkflowView workflow = workflowService.getWorkflow(path);
                    Map<String, Object> latestSnapshot = Map.of();
                    if (workflow.instanceState() != null && !workflow.instanceState().isBlank()) {
                        latestSnapshot = objectMapper.readValue(workflow.instanceState(), Map.class);
                    }
                    return Map.of(
                            "status", "OK",
                            "path", path,
                            "count", instances.size(),
                            "instances", instances.stream().limit(limit).toList(),
                            "latestSnapshot", latestSnapshot
                    );
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool cancelWorkflowInstanceTool(
            WorkflowInstanceCancelService cancelService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "cancel_workflow_instance";
            }

            @Override
            public String description() {
                return "Cancel a running workflow instance. Args: instanceId, optional reason, cancelledBy.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String instanceId = stringArg(arguments, "instanceId");
                if (instanceId.isBlank()) {
                    return Map.of("status", "ERROR", "error", "instanceId is required");
                }
                try {
                    Map<String, Object> result = cancelService.cancel(
                            instanceId,
                            stringArg(arguments, "reason"),
                            stringArg(arguments, "detailJson"),
                            stringArg(arguments, "cancelledBy")
                    );
                    Map<String, Object> response = new LinkedHashMap<>(result);
                    response.put("status", "OK");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static PlatformAgentTool signalWorkflowInstanceTool(
            WorkflowService workflowService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "signal_workflow_instance";
            }

            @Override
            public String description() {
                return "Deliver BPMN signal to waiting instance. Args: instanceId, signal, optional operatorId.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String instanceId = stringArg(arguments, "instanceId");
                String signal = stringArg(arguments, "signal");
                if (instanceId.isBlank() || signal.isBlank()) {
                    return Map.of("status", "ERROR", "error", "instanceId and signal are required");
                }
                try {
                    Map<String, Object> result = workflowService.deliverSignal(
                            instanceId,
                            signal,
                            stringArg(arguments, "operatorId")
                    );
                    Map<String, Object> response = new LinkedHashMap<>(result);
                    response.put("status", "OK");
                    return response;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static Map<String, Object> readWorkflow(
            String path,
            AgentContext context,
            WorkflowService workflowService,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        var auth = context.authentication();
        if (!tenantScopeService.isPathVisible(path, auth)) {
            return Map.of("status", "ERROR", "error", "Tenant scope denied for " + path);
        }
        objectAccessService.requireRead(path, auth);
        try {
            WorkflowService.WorkflowView view = workflowService.getWorkflow(path);
            return Map.of("status", "OK", "workflow", workflowSummary(view));
        } catch (Exception ex) {
            return Map.of("status", "ERROR", "error", ex.getMessage());
        }
    }

    private static boolean requireWorkflowWrite(
            String path,
            org.springframework.security.core.Authentication auth,
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            TenantScopeService tenantScopeService
    ) {
        if (!tenantScopeService.isPathVisible(path, auth)) {
            return false;
        }
        objectAccessService.requireWrite(path, auth);
        PlatformObject node = objectManager.require(path);
        return node.type() == ObjectType.WORKFLOW;
    }

    private static Map<String, Object> workflowSummary(WorkflowService.WorkflowView view) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("path", view.path());
        row.put("title", view.title());
        row.put("status", view.status() != null ? view.status().name() : null);
        row.put("bpmnLength", view.bpmnXml() != null ? view.bpmnXml().length() : 0);
        row.put("hasBpmn", view.bpmnXml() != null && !view.bpmnXml().isBlank());
        row.put("operatorAppId", view.operatorAppId());
        row.put("lastRunAt", view.lastRunAt());
        row.put("triggerJson", view.triggerJson());
        return row;
    }

    private static Map<String, Object> instanceRow(WorkflowInstanceEntity entity) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("instanceId", entity.getId());
        row.put("workflowPath", entity.getWorkflowPath());
        row.put("status", entity.getStatus());
        row.put("currentNodeId", entity.getCurrentNodeId());
        row.put("assignee", entity.getAssignee());
        row.put("triggerObjectPath", entity.getTriggerObjectPath());
        row.put("startedAt", entity.getStartedAt() != null ? entity.getStartedAt().toString() : null);
        row.put("completedAt", entity.getCompletedAt() != null ? entity.getCompletedAt().toString() : null);
        return row;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        if (args == null) {
            return "";
        }
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intArg(Map<String, Object> args, String key, int fallback) {
        Object raw = args != null ? args.get(key) : null;
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text.trim());
        }
        return fallback;
    }
}
