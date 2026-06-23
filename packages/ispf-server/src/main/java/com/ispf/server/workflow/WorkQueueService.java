package com.ispf.server.workflow;

import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.persistence.WorkflowUserTaskRepository;
import com.ispf.server.persistence.entity.WorkflowUserTaskEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WorkQueueService {

    private static final String PLATFORM_WORKFLOW_PREFIX = "root.platform.workflows";

    private final WorkflowUserTaskRepository userTaskRepository;
    private final WorkflowService workflowService;

    public WorkQueueService(WorkflowUserTaskRepository userTaskRepository, WorkflowService workflowService) {
        this.userTaskRepository = userTaskRepository;
        this.workflowService = workflowService;
    }

    @Transactional(readOnly = true)
    public List<WorkQueueItem> listOpenTasks(int limit) {
        return listOpenTasks(limit, null);
    }

    @Transactional(readOnly = true)
    public List<WorkQueueItem> listOpenTasks(int limit, String operatorAppId) {
        int pageSize = Math.max(1, limit);
        PageRequest page = PageRequest.of(0, pageSize);
        List<String> statuses = List.of("OPEN", "CLAIMED");
        List<WorkflowUserTaskEntity> tasks;
        if (operatorAppId != null && !operatorAppId.isBlank()) {
            tasks = listOpenTasksForOperatorApp(statuses, operatorAppId.trim(), pageSize);
        } else {
            tasks = userTaskRepository.findByStatusInOrderByCreatedAtDesc(statuses, page);
        }
        return tasks.stream()
                .map(this::toItem)
                .toList();
    }

    private List<WorkflowUserTaskEntity> listOpenTasksForOperatorApp(
            List<String> statuses,
            String appId,
            int limit
    ) {
        Map<String, WorkflowUserTaskEntity> merged = new LinkedHashMap<>();
        PageRequest page = PageRequest.of(0, limit);
        for (WorkflowUserTaskEntity task : userTaskRepository.findByStatusInAndOperatorAppIdOrderByCreatedAtDesc(
                statuses,
                appId,
                page
        )) {
            merged.put(task.getId(), task);
        }
        if ("platform".equals(appId) && merged.size() < limit) {
            PageRequest legacyPage = PageRequest.of(0, Math.max(limit * 3, limit));
            for (WorkflowUserTaskEntity task : userTaskRepository.findByStatusInOrderByCreatedAtDesc(
                    statuses,
                    legacyPage
            )) {
                if (merged.size() >= limit) {
                    break;
                }
                if (matchesLegacyPlatformTask(task)) {
                    merged.putIfAbsent(task.getId(), task);
                }
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(WorkflowUserTaskEntity::getCreatedAt).reversed())
                .limit(limit)
                .toList();
    }

    private boolean matchesLegacyPlatformTask(WorkflowUserTaskEntity task) {
        String assigned = task.getOperatorAppId();
        if (assigned != null && !assigned.isBlank()) {
            return false;
        }
        return task.getWorkflowPath() != null && task.getWorkflowPath().startsWith(PLATFORM_WORKFLOW_PREFIX);
    }

    private String resolveTaskOperatorAppId(WorkflowUserTaskEntity task) {
        String assigned = task.getOperatorAppId();
        if (assigned != null && !assigned.isBlank()) {
            return assigned;
        }
        return workflowService.resolveOperatorAppIdForPath(task.getWorkflowPath());
    }

    @Transactional
    public WorkQueueItem claimTask(String taskId, String operatorId) throws WorkflowException {
        WorkflowUserTaskEntity task = userTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if ("COMPLETED".equals(task.getStatus())) {
            return toItem(task);
        }
        if ("CLAIMED".equals(task.getStatus()) && task.getAssignee() != null
                && !operatorId.equals(task.getAssignee())) {
            throw new IllegalStateException("Task is assigned to another operator");
        }
        task.setStatus("CLAIMED");
        task.setAssignee(operatorId);
        task.setClaimedAt(Instant.now());
        userTaskRepository.save(task);
        workflowService.claimInstance(task.getInstanceId(), operatorId);
        return toItem(task);
    }

    @Transactional
    public WorkQueueItem completeTask(String taskId, String operatorId) throws WorkflowException {
        WorkflowUserTaskEntity task = userTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if ("COMPLETED".equals(task.getStatus())) {
            return toItem(task);
        }
        if ("CLAIMED".equals(task.getStatus()) && task.getAssignee() != null
                && !task.getAssignee().equals(operatorId)) {
            throw new IllegalStateException("Task is assigned to another operator");
        }
        workflowService.completeUserTask(task.getInstanceId(), task.getTaskNodeId(), operatorId);
        markTaskCompleted(taskId);
        WorkflowUserTaskEntity updated = userTaskRepository.findById(taskId).orElseThrow();
        return toItem(updated);
    }

    @Transactional
    public void markTaskCompleted(String taskId) {
        WorkflowUserTaskEntity task = userTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        if (!"COMPLETED".equals(task.getStatus())) {
            task.setStatus("COMPLETED");
            task.setCompletedAt(Instant.now());
            userTaskRepository.save(task);
        }
    }

    @Transactional
    public void markInstanceTasksCompleted(String instanceId) {
        userTaskRepository.findByInstanceId(instanceId).stream()
                .filter(task -> !"COMPLETED".equals(task.getStatus()))
                .forEach(task -> {
                    task.setStatus("COMPLETED");
                    task.setCompletedAt(Instant.now());
                    userTaskRepository.save(task);
                });
    }

    private WorkQueueItem toItem(WorkflowUserTaskEntity task) {
        return new WorkQueueItem(
                task.getId(),
                task.getInstanceId(),
                task.getWorkflowPath(),
                resolveTaskOperatorAppId(task),
                task.getTaskNodeId(),
                task.getTitle(),
                task.getInstructions(),
                task.getAssigneeRole(),
                task.getStatus(),
                task.getAssignee(),
                task.getCreatedAt(),
                task.getClaimedAt(),
                task.getCompletedAt()
        );
    }

    public record WorkQueueItem(
            String id,
            String instanceId,
            String workflowPath,
            String operatorAppId,
            String taskNodeId,
            String title,
            String instructions,
            String assigneeRole,
            String status,
            String assignee,
            Instant createdAt,
            Instant claimedAt,
            Instant completedAt
    ) {
    }
}
