package com.ispf.server.ai.agent;

import com.ispf.core.object.ObjectType;
import com.ispf.server.api.dto.ObjectDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGroundTruthGuardTest {

    @Test
    void blocksCreateObjectWithoutDiscovery() {
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of(
                        "parentPath", "root.platform.devices.pump-station",
                        "name", "pump-01",
                        "type", "DEVICE"
                ),
                List.of()
        );
        assertThat(block).isPresent();
        assertThat(block.get().error()).contains("not discovered");
        assertThat(block.get().hint()).contains("list_objects parent=");
    }

    @Test
    void allowsCreateObjectAfterListObjectsOnParent() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.devices", List.of(
                        Map.of("path", "root.platform.devices.pump-station")
                ))
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.devices.pump-station", "name", "pump-01"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void allowsCreateObjectUnderListedFolder() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.devices", List.of())
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.devices", "name", "new-folder"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void allowsChildAfterParentCreatedInSameTurn() {
        List<Map<String, Object>> steps = List.of(
                createObjectStep("root.platform.devices.new-station")
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_virtual_device",
                Map.of("parentPath", "root.platform.devices.new-station", "name", "pump-01"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void blocksApplyRelativeModelWithoutCatalog() {
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "apply_relative_model",
                Map.of("objectPath", "root.platform.devices.d1", "modelName", "virtual-lab-v1"),
                List.of(
                        listObjectsStep("root.platform.devices", List.of(Map.of("path", "root.platform.devices.d1")))
                )
        );
        assertThat(block).isPresent();
        assertThat(block.get().hint()).contains("list_relative_models");
    }

    @Test
    void allowsApplyRelativeModelAfterListRelativeModels() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.devices", List.of(Map.of("path", "root.platform.devices.d1"))),
                relativeModelsStep(List.of(Map.of("modelName", "virtual-lab-v1")))
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "apply_relative_model",
                Map.of("objectPath", "root.platform.devices.d1", "modelName", "virtual-lab-v1"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void allowsMimicFolderAfterListPlatformWithObjectDtoChildren() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "list_objects",
                        "arguments", Map.of("parent", "root.platform"),
                        "result", Map.of(
                                "status", "OK",
                                "parent", "root.platform",
                                "objects", List.of(mimicsFolderDto())
                        )
                )
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.mimics", "name", "pump-station-hmi", "type", "MIMIC"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void allowsCreateObjectOnExactListedParentPath() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.mimics", List.of())
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.mimics", "name", "pump-station-hmi", "type", "MIMIC"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void allowsAfterGetObjectOnParent() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "get_object",
                        "arguments", Map.of("path", "root.platform.mimics"),
                        "result", Map.of("status", "OK", "path", "root.platform.mimics")
                )
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.mimics", "name", "hmi-01"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void allowsAfterSearchObjectsReturnsParent() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "search_objects",
                        "arguments", Map.of("query", "mimics"),
                        "result", Map.of(
                                "status", "OK",
                                "objects", List.of(Map.of("path", "root.platform.mimics"))
                        )
                )
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.mimics", "name", "hmi-01"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void allowsListObjectsWithParentPathAlias() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "list_objects",
                        "arguments", Map.of("parentPath", "root.platform.dashboards"),
                        "result", Map.of("status", "OK", "parent", "root.platform.dashboards", "objects", List.of())
                )
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.dashboards", "name", "pump-dash"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void ignoresFailedDiscoverySteps() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "list_objects",
                        "arguments", Map.of("parent", "root.platform.mimics"),
                        "result", Map.of("status", "ERROR", "error", "denied")
                )
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.mimics", "name", "hmi"),
                steps
        );
        assertThat(block).isPresent();
    }

    @Test
    void blocksInstantiateInstanceTypeWithoutCatalog() {
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "instantiate_instance_type",
                Map.of("instanceType", "base-sensor-v1", "parentPath", "root.platform.instances"),
                List.of()
        );
        assertThat(block).isPresent();
    }

    @Test
    void allowsInstantiateAfterListInstanceTypes() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "list_instance_types",
                        "arguments", Map.of(),
                        "result", Map.of(
                                "status", "OK",
                                "models", List.of(Map.of("modelName", "base-sensor-v1"))
                        )
                )
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "instantiate_instance_type",
                Map.of("instanceType", "base-sensor-v1"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void allowsVirtualDeviceAfterListVirtualProfiles() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.devices", List.of()),
                Map.of(
                        "type", "tool",
                        "tool", "list_virtual_profiles",
                        "arguments", Map.of(),
                        "result", Map.of(
                                "status", "OK",
                                "profiles", List.of(Map.of("profile", "lab", "templateId", "virtual-lab-v1"))
                        )
                )
        );
        assertThat(AgentGroundTruthGuard.isParentGrounded(steps, "root.platform.devices")).isTrue();
        assertThat(AgentGroundTruthGuard.isModelGrounded(steps, "lab")).isTrue();
    }

    @Test
    void isParentGroundedFalseForUnlistedDeepPath() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.devices", List.of(
                        Map.of("path", "root.platform.devices.station-a")
                ))
        );
        assertThat(AgentGroundTruthGuard.isParentGrounded(steps, "root.platform.devices.station-a.pump-01")).isFalse();
    }

    @Test
    void isParentGroundedTrueForDeepPathAfterListingIntermediate() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.devices.station-a", List.of(
                        Map.of("path", "root.platform.devices.station-a")
                ))
        );
        assertThat(AgentGroundTruthGuard.isParentGrounded(steps, "root.platform.devices.station-a")).isTrue();
    }

    @Test
    void allowsCreateUnderWorkflowsAfterListPlatform() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform", List.of(
                        Map.of("path", "root.platform.workflows", "name", "workflows")
                ))
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "create_object",
                Map.of("parentPath", "root.platform.workflows", "name", "hydraulic-shock", "type", "WORKFLOW"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void blocksSaveWorkflowBpmnWithoutCreate() {
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "save_workflow_bpmn",
                Map.of("path", "root.platform.workflows.hydraulic-shock", "bpmnXml", "<bpmn/>"),
                List.of(listObjectsStep("root", List.of(Map.of("path", "root.platform", "name", "platform"))))
        );
        assertThat(block).isPresent();
        assertThat(block.get().hint()).contains("create_object");
    }

    @Test
    void allowsSaveWorkflowBpmnAfterCreateObject() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.workflows", List.of()),
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of(
                                "parentPath", "root.platform.workflows",
                                "name", "hydraulic-shock",
                                "type", "WORKFLOW"
                        ),
                        "result", Map.of(
                                "status", "OK",
                                "path", "root.platform.workflows.hydraulic-shock"
                        )
                )
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "save_workflow_bpmn",
                Map.of("path", "root.platform.workflows.hydraulic-shock", "bpmnXml", "<bpmn/>"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void blocksSaveMimicDiagramWithoutCreate() {
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "save_mimic_diagram",
                Map.of("path", "root.platform.mimics.pump-hmi", "elements", List.of()),
                List.of(listObjectsStep("root.platform.mimics", List.of()))
        );
        assertThat(block).isPresent();
        assertThat(block.get().hint()).contains("create_object");
    }

    @Test
    void allowsSetDashboardLayoutAfterCreate() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.dashboards", List.of()),
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of(
                                "parentPath", "root.platform.dashboards",
                                "name", "pump-dash",
                                "type", "DASHBOARD"
                        ),
                        "result", Map.of("status", "OK", "path", "root.platform.dashboards.pump-dash")
                )
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "set_dashboard_layout",
                Map.of("path", "root.platform.dashboards.pump-dash", "template", "empty"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void allowsConfigureOnExistingObjectAfterGetObject() {
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "get_object",
                        "arguments", Map.of("path", "root.platform.devices.pump-01"),
                        "result", Map.of("status", "OK", "path", "root.platform.devices.pump-01")
                )
        );
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "configure_driver",
                Map.of("path", "root.platform.devices.pump-01"),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void doesNotGuardUnrelatedTools() {
        assertThat(AgentGroundTruthGuard.checkBeforeTool("list_variables", Map.of("path", "root"), List.of()))
                .isEmpty();
        assertThat(AgentGroundTruthGuard.checkBeforeTool("get_workflow", Map.of("path", "root.platform.workflows.x"), List.of()))
                .isEmpty();
    }

    @Test
    void allowsSaveMimicAfterObjectExistsError() {
        String mimicPath = "root.platform.mimics.pump-station-mimic";
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.mimics", List.of()),
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of(
                                "parentPath", "root.platform.mimics",
                                "name", "pump-station-mimic",
                                "type", "MIMIC"
                        ),
                        "result", Map.of(
                                "status", "ERROR",
                                "error", "Object exists: " + mimicPath,
                                "existingPath", mimicPath,
                                "hint", "Reuse this object"
                        )
                )
        );
        assertThat(AgentGroundTruthGuard.isObjectPathGrounded(steps, mimicPath)).isTrue();
        var block = AgentGroundTruthGuard.checkBeforeTool(
                "save_mimic_diagram",
                Map.of("path", mimicPath, "elements", List.of()),
                steps
        );
        assertThat(block).isEmpty();
    }

    @Test
    void groundsParentFromObjectExistsError() {
        String mimicPath = "root.platform.mimics.pump-station-mimic";
        List<Map<String, Object>> steps = List.of(
                Map.of(
                        "type", "tool",
                        "tool", "create_object",
                        "arguments", Map.of("parentPath", "root.platform.mimics", "name", "pump-station-mimic"),
                        "result", Map.of(
                                "status", "ERROR",
                                "error", "Object exists: " + mimicPath,
                                "existingPath", mimicPath
                        )
                )
        );
        assertThat(AgentGroundTruthGuard.isParentGrounded(steps, "root.platform.mimics")).isTrue();
    }

    private static ObjectDto mimicsFolderDto() {
        return new ObjectDto(
                "id-mimics",
                "root.platform.mimics",
                ObjectType.CUSTOM,
                "SCADA Mimics",
                null,
                null,
                null,
                Instant.EPOCH,
                0,
                0L,
                null,
                null,
                List.of(),
                List.of(),
                false,
                null,
                null,
                List.of(),
                false,
                null,
                false,
                null,
                null,
                false,
                false
        );
    }

    private static Map<String, Object> listObjectsStep(String parent, List<Map<String, Object>> objects) {
        return Map.of(
                "type", "tool",
                "tool", "list_objects",
                "arguments", Map.of("parent", parent),
                "result", Map.of("status", "OK", "parent", parent, "objects", objects)
        );
    }

    private static Map<String, Object> createObjectStep(String path) {
        return Map.of(
                "type", "tool",
                "tool", "create_object",
                "arguments", Map.of("parentPath", "root.platform.devices", "name", "new-station"),
                "result", Map.of("status", "OK", "path", path)
        );
    }

    private static Map<String, Object> relativeModelsStep(List<Map<String, Object>> models) {
        return Map.of(
                "type", "tool",
                "tool", "list_relative_models",
                "arguments", Map.of(),
                "result", Map.of("status", "OK", "models", models)
        );
    }

    @Test
    void groundsParentWithCaseInsensitiveSegmentMatch() {
        List<Map<String, Object>> steps = List.of(
                listObjectsStep("root.platform.devices", List.of(
                        Map.of("path", "root.platform.devices.NM-1", "name", "NM-1")
                ))
        );
        assertThat(AgentGroundTruthGuard.isObjectPathGrounded(steps, "root.platform.devices.nm-1")).isTrue();
        assertThat(AgentGroundTruthGuard.resolveCanonicalPath(steps, "root.platform.devices.nm-1"))
                .isEqualTo("root.platform.devices.NM-1");
    }
}
