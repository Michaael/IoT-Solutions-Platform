package com.ispf.server.api;

import com.ispf.plugin.workflow.WorkflowException;
import com.ispf.server.workflow.WorkQueueService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/work-queue")
public class WorkQueueController {

    private final WorkQueueService workQueueService;

    public WorkQueueController(WorkQueueService workQueueService) {
        this.workQueueService = workQueueService;
    }

    @GetMapping
    public List<WorkQueueService.WorkQueueItem> list(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String operatorAppId
    ) {
        return workQueueService.listOpenTasks(limit, operatorAppId);
    }

    @PostMapping("/claim")
    public WorkQueueService.WorkQueueItem claim(
            @RequestParam String taskId,
            @RequestParam(defaultValue = "operator") String operatorId
    ) throws WorkflowException {
        return workQueueService.claimTask(taskId, operatorId);
    }

    @PostMapping("/complete")
    public WorkQueueService.WorkQueueItem complete(
            @RequestParam String taskId,
            @RequestParam(defaultValue = "operator") String operatorId
    ) throws WorkflowException {
        return workQueueService.completeTask(taskId, operatorId);
    }
}
