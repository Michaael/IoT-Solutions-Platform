package com.ispf.server.ai.context;

import com.ispf.driver.DriverMetadata;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.config.AiProperties;
import com.ispf.server.driver.DriverCatalog;
import com.ispf.server.object.ObjectManager;
import com.ispf.core.object.PlatformObject;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Compact platform knowledge injected into the tree-first agent system prompt (FW-45).
 */
@Service
public class PlatformBriefingService {

    private static final int LIVE_CHILDREN_CAP = 30;

    private static final List<Map<String, String>> FEATURE_INDEX = List.of(
            feature("bundles", "Bundle deploy", "manifest JSON, migrations, functions, dashboards", "bundle deploy import"),
            feature("bff", "BFF invoke", "POST /api/v1/bff/invoke application functions", "bff invoke function"),
            feature("workflows", "BPMN workflows", "workflows[] in bundle, publish_nats, fire_event tasks", "workflow bpmn"),
            feature("correlators", "Event correlators & alerts", "configure_alert, configure_correlator, list_automation", "correlator alert automation"),
            feature("operator-ui", "Operator HMI", "configure_operator_ui defaultDashboard dashboards[]", "operator hmi default dashboard"),
            feature("federation", "Federation bind", "overlay remote peer on local path", "federation peer bind"),
            feature("dashboards", "Dashboards", "DASHBOARD objects, layout.widgets[], widget types", "dashboard widget layout"),
            feature("events", "Event catalog", "events[] in bundle, WS subscribe_events", "event catalog subscribe"),
            feature("virtual", "Virtual driver", "profiles demo, meter, weighbridge, rack-signals, lab, unified", "virtual profile meter"),
            feature("history", "Variable history", "historyEnabled, charts, export CSV", "variable history trend"),
            feature("ai-studio", "AI Studio", "tree-first agent, validate_bundle, import_package", "ai agent studio")
    );

    private static final List<Map<String, String>> VIRTUAL_PROFILES = List.of(
            Map.of("profile", "demo", "vars", "temperature, status", "use", "default simulator"),
            Map.of("profile", "meter", "vars", "meterLiters, flowRate, filling", "use", "filling simulation"),
            Map.of("profile", "weighbridge", "vars", "grossWeight, tareKg", "use", "weighbridge + meter"),
            Map.of("profile", "rack-signals", "vars", "gasPresent, groundConnected", "use", "rack safety signals"),
            Map.of("profile", "lab", "vars", "sineWave, sawtoothWave, triangleWave, status", "use", "wave simulators / virtual cluster"),
            Map.of("profile", "unified", "vars", "all types: waves, geo, tables, binary, meter, health", "use", "showcase / virtual-unified-v1")
    );

    private final AiProperties aiProperties;
    private final ContextPackService contextPackService;
    private final DriverCatalog driverCatalog;
    private final ApplicationDataStore applicationDataStore;
    private final ApplicationBundleSnapshotStore bundleSnapshotStore;
    private final ObjectManager objectManager;

    public PlatformBriefingService(
            AiProperties aiProperties,
            ContextPackService contextPackService,
            DriverCatalog driverCatalog,
            ApplicationDataStore applicationDataStore,
            ApplicationBundleSnapshotStore bundleSnapshotStore,
            ObjectManager objectManager
    ) {
        this.aiProperties = aiProperties;
        this.contextPackService = contextPackService;
        this.driverCatalog = driverCatalog;
        this.applicationDataStore = applicationDataStore;
        this.bundleSnapshotStore = bundleSnapshotStore;
        this.objectManager = objectManager;
    }

    public String buildBriefing(String rootPath, boolean includeStaticKnowledge) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context pack: ").append(contextPackService.contextPackVersion()).append('\n');
        if (includeStaticKnowledge) {
            appendDrivers(sb);
            appendVirtualProfiles(sb);
            appendExamples(sb);
            appendFeatures(sb);
        }
        appendLiveSnapshot(sb, rootPath);
        return truncate(sb.toString(), aiProperties.getBriefingMaxChars());
    }

    private void appendDrivers(StringBuilder sb) {
        sb.append("\n### Drivers (driverId | name | maturity)\n");
        for (DriverMetadata driver : driverCatalog.list()) {
            sb.append("- ")
                    .append(driver.id())
                    .append(" | ")
                    .append(driver.name())
                    .append(" | ")
                    .append(driver.maturity().name())
                    .append('\n');
        }
        sb.append("Use list_drivers or get_driver_help for config templates.\n");
    }

    private void appendVirtualProfiles(StringBuilder sb) {
        sb.append("\n### Virtual driver profiles\n");
        for (Map<String, String> profile : VIRTUAL_PROFILES) {
            sb.append("- ")
                    .append(profile.get("profile"))
                    .append(": ")
                    .append(profile.get("vars"))
                    .append(" — ")
                    .append(profile.get("use"))
                    .append('\n');
        }
    }

    @SuppressWarnings("unchecked")
    private void appendExamples(StringBuilder sb) {
        sb.append("\n### Reference examples\n");
        Map<String, Object> pack = contextPackService.loadPack();
        Object summaries = pack.get("exampleSummaries");
        if (summaries instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    sb.append("- ")
                            .append(row.get("appId"))
                            .append(" | ")
                            .append(String.valueOf(row.get("purpose")))
                            .append(" | sections: ")
                            .append(String.valueOf(row.get("keySections")))
                            .append('\n');
                }
            }
            return;
        }
        Object examples = pack.get("examples");
        if (examples instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> example) {
                    String appId = String.valueOf(
                            example.get("packageId") != null ? example.get("packageId") : example.get("appId")
                    );
                    Object sections = example.get("sections");
                    sb.append("- ")
                            .append(appId)
                            .append(" | sections: ")
                            .append(sections)
                            .append('\n');
                }
            }
        }
        sb.append("Use list_examples or get_example_bundle for manifest details.\n");
    }

    private void appendFeatures(StringBuilder sb) {
        sb.append("\n### Platform features\n");
        Object fromPack = contextPackService.loadPack().get("featureIndex");
        if (fromPack instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> row) {
                    sb.append("- ")
                            .append(row.get("title"))
                            .append(": ")
                            .append(row.get("description"))
                            .append('\n');
                }
            }
            return;
        }
        for (Map<String, String> feature : FEATURE_INDEX) {
            sb.append("- ")
                    .append(feature.get("title"))
                    .append(": ")
                    .append(feature.get("description"))
                    .append('\n');
        }
    }

    private void appendLiveSnapshot(StringBuilder sb, String rootPath) {
        sb.append("\n### Live instance snapshot\n");
        List<Map<String, Object>> apps = new ArrayList<>();
        for (Map<String, Object> app : applicationDataStore.listAllApps()) {
            String appId = String.valueOf(app.get("app_id"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("appId", appId);
            row.put("displayName", app.get("display_name"));
            Optional<ApplicationBundleSnapshotStore.BundleSnapshot> active = bundleSnapshotStore.findActive(appId);
            active.ifPresent(snapshot -> row.put("bundleVersion", snapshot.bundleVersion()));
            apps.add(row);
        }
        if (apps.isEmpty()) {
            sb.append("Deployed applications: (none registered)\n");
        } else {
            sb.append("Deployed applications:\n");
            for (Map<String, Object> app : apps) {
                sb.append("- ")
                        .append(app.get("appId"));
                if (app.containsKey("bundleVersion")) {
                    sb.append(" v").append(app.get("bundleVersion"));
                }
                sb.append('\n');
            }
        }

        String effectiveRoot = rootPath == null || rootPath.isBlank() ? "root" : rootPath.trim();
        sb.append("Tree under ").append(effectiveRoot).append(" (direct children, max ")
                .append(LIVE_CHILDREN_CAP)
                .append("):\n");
        try {
            List<PlatformObject> children = objectManager.tree().childrenOf(effectiveRoot);
            int count = 0;
            for (PlatformObject child : children) {
                if (count >= LIVE_CHILDREN_CAP) {
                    sb.append("- ... (truncated)\n");
                    break;
                }
                sb.append("- ")
                        .append(child.path())
                        .append(" [")
                        .append(child.type())
                        .append("]\n");
                count++;
            }
            if (count == 0) {
                sb.append("- (no children)\n");
            }
        } catch (Exception ex) {
            sb.append("- (unable to list: ").append(ex.getMessage()).append(")\n");
        }
    }

    private static Map<String, String> feature(String id, String title, String description, String keywords) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("title", title);
        row.put("description", description);
        row.put("keywords", keywords);
        return row;
    }

    private static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars - 20) + "\n… (truncated)";
    }
}
