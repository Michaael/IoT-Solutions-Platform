package com.ispf.server.application.bundle;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.workflow.WorkflowLifecycleStatus;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.workflow.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationBundleMetadataService {

    private final ObjectManager objectManager;
    private final DashboardService dashboardService;
    private final WorkflowService workflowService;

    public ApplicationBundleMetadataService(
            ObjectManager objectManager,
            DashboardService dashboardService,
            WorkflowService workflowService
    ) {
        this.objectManager = objectManager;
        this.dashboardService = dashboardService;
        this.workflowService = workflowService;
    }

    @Transactional
    public DeployOutcome deployObject(ApplicationBundleDeployService.BundleObject object) {
        String path = objectManager.tree().resolveChildPath(object.parentPath(), object.name());
        var existing = objectManager.tree().findByPath(path);
        if (existing.isPresent()) {
            PlatformObject node = existing.get();
            if (object.displayName() != null) {
                objectManager.updateInfo(path, object.displayName(), object.description() != null ? object.description() : node.description());
            }
            return DeployOutcome.UPDATED;
        }
        ObjectType type = ObjectType.valueOf(object.type());
        PlatformObject node = objectManager.create(
                object.parentPath(),
                object.name(),
                type,
                object.displayName() != null ? object.displayName() : object.name(),
                object.description() != null ? object.description() : "",
                object.templateId()
        );
        if (type == ObjectType.DASHBOARD) {
            dashboardService.ensureDashboardStructure(node.path());
        }
        if (type == ObjectType.WORKFLOW) {
            workflowService.ensureWorkflowStructure(node.path());
        }
        return DeployOutcome.APPLIED;
    }

    @Transactional
    public DeployOutcome deployDashboard(ApplicationBundleDeployService.BundleDashboard dashboard) {
        ensureLeafObject(
                dashboard.path(),
                ObjectType.DASHBOARD,
                dashboard.title() != null ? dashboard.title() : "Dashboard",
                "dashboard-v1"
        );
        dashboardService.ensureDashboardStructure(dashboard.path());
        if (dashboard.title() != null) {
            dashboardService.updateTitle(dashboard.path(), dashboard.title());
        }
        if (dashboard.layoutJson() != null) {
            dashboardService.saveLayout(dashboard.path(), dashboard.layoutJson());
        }
        return DeployOutcome.APPLIED;
    }

    @Transactional
    public DeployOutcome deployWorkflow(ApplicationBundleDeployService.BundleWorkflow workflow) throws Exception {
        ensureLeafObject(
                workflow.path(),
                ObjectType.WORKFLOW,
                workflow.path().substring(workflow.path().lastIndexOf('.') + 1),
                "workflow-v1"
        );
        workflowService.ensureWorkflowStructure(workflow.path());
        if (workflow.bpmnXml() != null && !workflow.bpmnXml().isBlank()) {
            workflowService.saveBpmn(workflow.path(), workflow.bpmnXml());
        }
        if (workflow.status() != null && !workflow.status().isBlank()) {
            workflowService.updateStatus(workflow.path(), WorkflowLifecycleStatus.valueOf(workflow.status()));
        }
        return DeployOutcome.APPLIED;
    }

    private void ensureLeafObject(String path, ObjectType type, String displayName, String templateId) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            return;
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot <= 0) {
            throw new IllegalArgumentException("Invalid object path: " + path);
        }
        objectManager.create(
                path.substring(0, lastDot),
                path.substring(lastDot + 1),
                type,
                displayName,
                "",
                templateId
        );
    }

    public enum DeployOutcome {
        APPLIED,
        UPDATED,
        SKIPPED
    }
}
