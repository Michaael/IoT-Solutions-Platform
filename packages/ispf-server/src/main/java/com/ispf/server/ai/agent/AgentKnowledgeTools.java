package com.ispf.server.ai.agent;

import com.ispf.driver.DriverMetadata;
import com.ispf.server.ai.context.ContextPackSearchService;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.driver.DriverCatalog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class AgentKnowledgeTools {

    private AgentKnowledgeTools() {
    }

    static List<PlatformAgentTool> all(
            ContextPackSearchService contextPackSearchService,
            DriverCatalog driverCatalog,
            ApplicationDataStore applicationDataStore,
            ApplicationBundleSnapshotStore bundleSnapshotStore
    ) {
        return List.of(
                searchContextTool(contextPackSearchService),
                listDriversTool(driverCatalog),
                getDriverHelpTool(contextPackSearchService),
                listExamplesTool(contextPackSearchService),
                getExampleBundleTool(contextPackSearchService),
                listApplicationsTool(applicationDataStore, bundleSnapshotStore)
        );
    }

    private static PlatformAgentTool searchContextTool(ContextPackSearchService searchService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "search_context";
            }

            @Override
            public String description() {
                return "Search ISPF platform knowledge (docs, drivers, features, examples). "
                        + "Args: query (string), optional topic (drivers|workflows|dashboards|examples|features|all).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                return searchService.search(
                        stringArg(arguments, "query"),
                        optionalString(arguments, "topic")
                );
            }
        };
    }

    private static PlatformAgentTool listDriversTool(DriverCatalog driverCatalog) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_drivers";
            }

            @Override
            public String description() {
                return "List device drivers on this platform. Args: optional query, optional maturity (PRODUCTION|BETA|STUB).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                String query = stringArg(arguments, "query").toLowerCase(Locale.ROOT);
                String maturityFilter = stringArg(arguments, "maturity").toUpperCase(Locale.ROOT);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (DriverMetadata driver : driverCatalog.list()) {
                    if (!maturityFilter.isBlank() && !driver.maturity().name().equals(maturityFilter)) {
                        continue;
                    }
                    String haystack = (driver.id() + " " + driver.name() + " " + driver.description()).toLowerCase(Locale.ROOT);
                    if (!query.isBlank() && !haystack.contains(query)) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("driverId", driver.id());
                    row.put("name", driver.name());
                    row.put("maturity", driver.maturity().name());
                    row.put("description", driver.description());
                    rows.add(row);
                }
                return Map.of("status", "OK", "count", rows.size(), "drivers", rows);
            }
        };
    }

    private static PlatformAgentTool getDriverHelpTool(ContextPackSearchService searchService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_driver_help";
            }

            @Override
            public String description() {
                return "Driver config help from context pack. Args: driverId (required).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                return searchService.driverHelp(stringArg(arguments, "driverId"));
            }
        };
    }

    private static PlatformAgentTool listExamplesTool(ContextPackSearchService searchService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_examples";
            }

            @Override
            public String description() {
                return "List reference bundle examples (mes-reference, warehouse, lab-training, …).";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                List<Map<String, Object>> examples = searchService.listExampleSummaries();
                return Map.of("status", "OK", "count", examples.size(), "examples", examples);
            }
        };
    }

    private static PlatformAgentTool getExampleBundleTool(ContextPackSearchService searchService) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "get_example_bundle";
            }

            @Override
            public String description() {
                return "Get reference bundle manifest subset. Args: appId (required), optional sections (array of strings).";
            }

            @Override
            @SuppressWarnings("unchecked")
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                List<String> sections = null;
                Object raw = arguments.get("sections");
                if (raw instanceof List<?> list) {
                    sections = list.stream().map(String::valueOf).toList();
                }
                return searchService.exampleBundle(stringArg(arguments, "appId"), sections);
            }
        };
    }

    private static PlatformAgentTool listApplicationsTool(
            ApplicationDataStore applicationDataStore,
            ApplicationBundleSnapshotStore bundleSnapshotStore
    ) {
        return new PlatformAgentTool() {
            @Override
            public String name() {
                return "list_applications";
            }

            @Override
            public String description() {
                return "List registered applications and active bundle versions on this instance.";
            }

            @Override
            public Map<String, Object> execute(Map<String, Object> arguments, AgentContext context) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Map<String, Object> app : applicationDataStore.listAllApps()) {
                    String appId = String.valueOf(app.get("app_id"));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("appId", appId);
                    row.put("displayName", app.get("display_name"));
                    row.put("schemaName", app.get("schema_name"));
                    Optional<ApplicationBundleSnapshotStore.BundleSnapshot> active = bundleSnapshotStore.findActive(appId);
                    active.ifPresent(snapshot -> {
                        row.put("bundleVersion", snapshot.bundleVersion());
                        row.put("deployedAt", snapshot.deployedAt().toString());
                    });
                    rows.add(row);
                }
                return Map.of("status", "OK", "count", rows.size(), "applications", rows);
            }
        };
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        String value = stringArg(args, key);
        return value.isBlank() ? null : value;
    }
}
