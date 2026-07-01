package com.ispf.server.ai.agent;

import com.ispf.core.model.DataRecord;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.core.object.Variable;
import com.ispf.plugin.model.ModelRegistry;
import com.ispf.server.bootstrap.LabModelBootstrap;
import com.ispf.server.driver.DeviceProvisioningService;
import com.ispf.server.driver.DriverBinding;
import com.ispf.server.driver.DriverRuntimeService;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import com.ispf.server.security.acl.ObjectAccessService;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * One-shot virtual device provisioning for the tree-first agent.
 */
final class AgentVirtualDeviceTools {

    private AgentVirtualDeviceTools() {
    }

    static List<PlatformAgentTool> all(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            ObjectTemplateService objectTemplateService,
            DeviceProvisioningService deviceProvisioningService,
            DriverRuntimeService driverRuntimeService,
            LabModelBootstrap labModelBootstrap,
            ModelRegistry modelRegistry,
            ObjectMapper objectMapper
    ) {
        return List.of(
                createVirtualDeviceTool(
                        objectManager,
                        objectAccessService,
                        objectTemplateService,
                        deviceProvisioningService,
                        driverRuntimeService,
                        labModelBootstrap,
                        modelRegistry,
                        objectMapper
                ),
                listVirtualProfilesTool(modelRegistry)
        );
    }

    private static PlatformAgentTool listVirtualProfilesTool(ModelRegistry modelRegistry) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_virtual_profiles";
            }

            @Override
            public String description() {
                return "List virtual driver profiles with templateId and expected telemetry variables. "
                        + "Prefer create_virtual_device over manual create_object+set_variable for simulators.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (String name : VirtualDeviceProfileCatalog.profileNames()) {
                    VirtualDeviceProfileCatalog.resolve(name)
                            .ifPresent(spec -> {
                                Map<String, Object> row = VirtualDeviceProfileCatalog.profileCatalogRow(name, spec);
                                row.put(
                                        "templateRegistered",
                                        modelRegistry.findByName(spec.templateId()).isPresent()
                                );
                                rows.add(row);
                            });
                }
                return Map.of(
                        "status", "OK",
                        "driverId", "virtual",
                        "profiles", rows,
                        "hint", "Pump/motor simulators: profile=lab (sineWave≈vibration). Flow: profile=meter. "
                                + "Pressure+temperature: profile=unified. Always verify with list_variables."
                );
            }
        };
    }

    private static PlatformAgentTool createVirtualDeviceTool(
            ObjectManager objectManager,
            ObjectAccessService objectAccessService,
            ObjectTemplateService objectTemplateService,
            DeviceProvisioningService deviceProvisioningService,
            DriverRuntimeService driverRuntimeService,
            LabModelBootstrap labModelBootstrap,
            ModelRegistry modelRegistry,
            ObjectMapper objectMapper
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "create_virtual_device";
            }

            @Override
            public String description() {
                return "Create DEVICE with virtual driver profile, model template, driver config, and start driver. "
                        + "Args: parentPath, name, displayName, profile (lab|meter|unified|demo|tank-farm-tank), "
                        + "pollIntervalMs?, autoStart? (default true). "
                        + "Returns path, variableCount, driverStatus. Call list_variables before finish.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String parentPath = stringArg(arguments, "parentPath");
                String name = stringArg(arguments, "name");
                String displayName = stringArg(arguments, "displayName");
                String profile = stringArg(arguments, "profile");
                if (parentPath.isBlank() || name.isBlank() || displayName.isBlank() || profile.isBlank()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "parentPath, name, displayName, profile are required",
                            "availableProfiles", VirtualDeviceProfileCatalog.profileNames()
                    );
                }
                Optional<VirtualDeviceProfileCatalog.ProfileSpec> specOpt =
                        VirtualDeviceProfileCatalog.resolve(profile);
                if (specOpt.isEmpty()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Unknown profile: " + profile,
                            "availableProfiles", VirtualDeviceProfileCatalog.profileNames()
                    );
                }
                VirtualDeviceProfileCatalog.ProfileSpec spec = specOpt.get();
                labModelBootstrap.ensureLabModels();
                if (modelRegistry.findByName(spec.templateId()).isEmpty()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Model not registered: " + spec.templateId()
                                    + " — server bootstrap issue; contact admin",
                            "templateId", spec.templateId(),
                            "profile", profile
                    );
                }
                if (objectManager.tree().findByPath(parentPath).isEmpty()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Parent not found: " + parentPath,
                            "hint", "Call list_objects parent=<existing folder> first; use parentPath from tool results."
                    );
                }
                var auth = context.authentication();
                objectAccessService.requireWrite(parentPath, auth);
                String fullPath = objectManager.tree().resolveChildPath(parentPath, name);
                if (objectManager.tree().findByPath(fullPath).isPresent()) {
                    return Map.of(
                            "status", "ERROR",
                            "error", "Object exists: " + fullPath,
                            "hint", "Reuse: get_object path=" + fullPath + " and list_variables.",
                            "existingPath", fullPath
                    );
                }

                try {
                    PlatformObject node = objectManager.create(
                            parentPath,
                            name,
                            ObjectType.DEVICE,
                            displayName,
                            null,
                            spec.templateId()
                    );
                    objectTemplateService.applyTemplate(node.path(), spec.templateId());
                    int pollMs = intArg(arguments, "pollIntervalMs", 2000);
                    deviceProvisioningService.provisionDriver(node.path(), "virtual", pollMs, false);

                    Map<String, String> configuration = parseConfigJson(objectMapper, spec.driverConfigJson());
                    Map<String, String> pointMappings = parseConfigJson(objectMapper, spec.driverPointMappingsJson());
                    driverRuntimeService.configure(
                            node.path(),
                            DriverBinding.of("virtual", pollMs, configuration, pointMappings)
                    );

                    boolean autoStart = boolArg(arguments, "autoStart", true);
                    DriverRuntimeService.DriverRuntimeStatus runtimeStatus = null;
                    if (autoStart) {
                        runtimeStatus = driverRuntimeService.start(node.path());
                        driverRuntimeService.pollNow(node.path());
                    }
                    objectManager.persistNodeTree(node.path());

                    PlatformObject saved = objectManager.require(node.path());
                    List<Map<String, Object>> variables = saved.variables().values().stream()
                            .map(AgentVirtualDeviceTools::variablePreview)
                            .toList();
                    long telemetryCount = variables.stream()
                            .filter(row -> !isDriverMeta(String.valueOf(row.get("name"))))
                            .count();

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("status", "OK");
                    result.put("path", node.path());
                    result.put("templateId", spec.templateId());
                    result.put("profile", profile.toLowerCase(Locale.ROOT));
                    result.put("variableCount", variables.size());
                    result.put("telemetryVariableCount", telemetryCount);
                    result.put("expectedVariables", spec.expectedVariables());
                    result.put("variables", variables);
                    if (runtimeStatus != null) {
                        result.put("driverStatus", runtimeStatus.status());
                        result.put("connected", runtimeStatus.connected());
                    }
                    if (telemetryCount < 1) {
                        result.put("warning", "No telemetry variables yet — run driver_control action=poll or list_variables");
                    }
                    return result;
                } catch (Exception ex) {
                    return Map.of("status", "ERROR", "error", ex.getMessage());
                }
            }
        };
    }

    private static Map<String, String> parseConfigJson(ObjectMapper objectMapper, String json) throws Exception {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> raw = objectMapper.readValue(json, Map.class);
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            out.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        return out;
    }

    private static boolean isDriverMeta(String name) {
        return name.startsWith("driver") || "timeZone".equals(name);
    }

    private static Map<String, Object> variablePreview(Variable variable) {
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("name", variable.name());
        preview.put("writable", variable.writable());
        variable.value().ifPresent(record -> {
            if (!record.rows().isEmpty()) {
                preview.put("value", record.firstRow());
            }
        });
        return preview;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean boolArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object value = args.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value != null) {
            return Boolean.parseBoolean(String.valueOf(value).trim());
        }
        return defaultValue;
    }
}
