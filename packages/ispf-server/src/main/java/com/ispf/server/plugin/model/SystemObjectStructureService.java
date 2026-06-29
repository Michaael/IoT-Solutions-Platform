package com.ispf.server.plugin.model;

import com.ispf.core.object.PlatformObject;
import com.ispf.plugin.model.ModelDefinition;
import com.ispf.plugin.model.ModelEngine;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.plugin.model.SystemIntrinsicModels;
import com.ispf.server.bootstrap.FixtureModelDefinitions;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies built-in variable schemas for platform system object types (embedded structure, not relative-model mixins).
 */
@Service
public class SystemObjectStructureService {

    private final ObjectManager objectManager;
    private final ModelRegistry modelRegistry;
    private final ModelEngine modelEngine;

    public SystemObjectStructureService(
            ObjectManager objectManager,
            ModelRegistry modelRegistry,
            ModelEngine modelEngine
    ) {
        this.objectManager = objectManager;
        this.modelRegistry = modelRegistry;
        this.modelEngine = modelEngine;
    }

    @Transactional
    public void ensureDataSourceStructure(String path) {
        if (objectManager.require(path).getVariable("schemaName").isPresent()) {
            return;
        }
        applyIntrinsic("data-source-v1", path);
    }

    @Transactional
    public void ensureScheduleStructure(String path) {
        if (objectManager.require(path).getVariable("scheduleId").isPresent()) {
            return;
        }
        applyIntrinsic("schedule-v1", path);
    }

    @Transactional
    public void ensureBindingStructure(String path) {
        if (objectManager.require(path).getVariable("query").isPresent()) {
            return;
        }
        applyIntrinsic("sql-binding-v1", path);
    }

    @Transactional
    public void ensureMigrationStructure(String path) {
        if (objectManager.require(path).getVariable("scriptId").isPresent()) {
            return;
        }
        applyIntrinsic("migration-v1", path);
    }

    @Transactional
    public void ensureDashboardStructure(String path) {
        if (objectManager.require(path).getVariable("layout").isPresent()) {
            return;
        }
        applyIntrinsic("dashboard-v1", path);
    }

    @Transactional
    public void ensureMimicStructure(String path) {
        if (objectManager.require(path).getVariable("diagram").isPresent()) {
            return;
        }
        applyIntrinsic("mimic-v1", path);
    }

    @Transactional
    public void ensureReportStructure(String path) {
        PlatformObject node = objectManager.require(path);
        if (node.getVariable("query").isPresent() || node.getVariable("reportType").isPresent()) {
            return;
        }
        applyIntrinsic("report-v1", path);
    }

    @Transactional
    public void ensureWorkflowStructure(String path) {
        if (objectManager.require(path).getVariable("bpmnXml").isPresent()) {
            return;
        }
        applyIntrinsic("workflow-v1", path);
    }

    @Transactional
    public void ensureAlertRuleStructure(String path) {
        if (objectManager.require(path).getVariable("targetObjectPath").isPresent()) {
            return;
        }
        applyIntrinsic("alert-rule-v1", path);
    }

    @Transactional
    public void ensureCorrelatorStructure(String path) {
        if (objectManager.require(path).getVariable("patternType").isPresent()) {
            return;
        }
        applyIntrinsic("correlator-v1", path);
    }

    @Transactional
    public void ensureDeviceDriverStructure(String path) {
        if (objectManager.require(path).getVariable("driverId").isPresent()) {
            return;
        }
        modelEngine.applyIntrinsicStructure(FixtureModelDefinitions.buildDeviceDriverModel(), path);
        objectManager.persistNodeTree(path);
    }

    private void applyIntrinsic(String modelName, String path) {
        modelRegistry.findByName(modelName).ifPresent(model -> {
            modelEngine.applyIntrinsicStructure(model, path);
            objectManager.persistNodeTree(path);
        });
    }
}
