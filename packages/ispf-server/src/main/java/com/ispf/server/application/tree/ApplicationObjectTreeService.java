package com.ispf.server.application.tree;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.application.binding.ApplicationSqlBindingStore;
import com.ispf.server.application.bundle.ApplicationBundleSnapshotStore;
import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.application.function.ApplicationFunctionHandler;
import com.ispf.server.application.function.ApplicationFunctionStore;
import com.ispf.server.application.schedule.PlatformSchedulerService;
import com.ispf.server.object.ObjectManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ApplicationObjectTreeService {

    public static final String APPLICATIONS_ROOT = "root.platform.applications";

    private final ObjectManager objectManager;
    private final ApplicationDataStore dataStore;
    private final ApplicationFunctionStore functionStore;
    private final ApplicationSqlBindingStore bindingStore;
    private final PlatformSchedulerService schedulerService;
    private final ApplicationBundleSnapshotStore snapshotStore;
    private final ObjectMapper objectMapper;

    public ApplicationObjectTreeService(
            ObjectManager objectManager,
            ApplicationDataStore dataStore,
            ApplicationFunctionStore functionStore,
            ApplicationSqlBindingStore bindingStore,
            PlatformSchedulerService schedulerService,
            ApplicationBundleSnapshotStore snapshotStore,
            ObjectMapper objectMapper
    ) {
        this.objectManager = objectManager;
        this.dataStore = dataStore;
        this.functionStore = functionStore;
        this.bindingStore = bindingStore;
        this.schedulerService = schedulerService;
        this.snapshotStore = snapshotStore;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncAllApplications() {
        ensureApplicationsRoot();
        for (Map<String, Object> app : dataStore.listAllApps()) {
            syncApplication(String.valueOf(app.get("app_id")));
        }
    }

    @Transactional
    public void syncApplication(String appId) {
        Map<String, Object> app = dataStore.findApp(appId)
                .orElseThrow(() -> new IllegalArgumentException("Application not registered: " + appId));

        ensureApplicationsRoot();
        String appPath = APPLICATIONS_ROOT + "." + sanitizeNodeName(appId);
        String displayName = String.valueOf(app.get("display_name"));
        String schemaName = String.valueOf(app.get("schema_name"));
        ensureNode(
                appPath,
                ObjectType.APPLICATION,
                displayName,
                "appId=" + appId + ", schema=" + schemaName,
                "application-v1"
        );

        syncFunctions(appId, appPath);
        syncSchedules(appId, appPath);
        syncBindings(appId, appPath);
        syncMigrations(appId, appPath);
        syncOperatorScreens(appId, appPath);
    }

    private void ensureApplicationsRoot() {
        ensureNode(
                APPLICATIONS_ROOT,
                ObjectType.APPLICATIONS,
                "Applications",
                "Deployed application bundles",
                "app-folder-v1"
        );
    }

    private void syncFunctions(String appId, String appPath) {
        String folderPath = appPath + ".functions";
        ensureFolder(folderPath, "Functions", "Deployed script functions");
        Set<String> expected = new HashSet<>();
        for (ApplicationFunctionHandler.DeployedFunction function : functionStore.listLatestByApp(appId)) {
            String nodeName = functionNodeName(function);
            expected.add(nodeName);
            ensureNode(
                    folderPath + "." + nodeName,
                    ObjectType.FUNCTION,
                    function.functionName(),
                    "objectPath=" + function.objectPath() + ", version=" + function.version(),
                    "application-function-v1"
            );
        }
        pruneChildren(folderPath, expected);
    }

    private void syncSchedules(String appId, String appPath) {
        String folderPath = appPath + ".schedules";
        ensureFolder(folderPath, "Schedules", "Platform scheduler entries");
        Set<String> expected = new HashSet<>();
        for (Map<String, Object> schedule : schedulerService.list()) {
            if (!appId.equals(String.valueOf(schedule.get("app_id")))) {
                continue;
            }
            String scheduleId = String.valueOf(schedule.get("schedule_id"));
            String nodeName = sanitizeNodeName(scheduleId);
            expected.add(nodeName);
            ensureNode(
                    folderPath + "." + nodeName,
                    ObjectType.SCHEDULE,
                    scheduleId,
                    "intervalMs=" + schedule.get("interval_ms") + ", action=" + schedule.get("action_type"),
                    "application-schedule-v1"
            );
        }
        pruneChildren(folderPath, expected);
    }

    private void syncBindings(String appId, String appPath) {
        String folderPath = appPath + ".bindings";
        ensureFolder(folderPath, "SQL bindings", "Application SQL variable bindings");
        Set<String> expected = new HashSet<>();
        for (ApplicationSqlBindingStore.SqlBinding binding : bindingStore.listByApp(appId)) {
            String nodeName = bindingNodeName(binding);
            expected.add(nodeName);
            ensureNode(
                    folderPath + "." + nodeName,
                    ObjectType.BINDING,
                    binding.variableName(),
                    "objectPath=" + binding.objectPath() + ", refresh=" + binding.refreshMode(),
                    "application-binding-v1"
            );
        }
        pruneChildren(folderPath, expected);
    }

    private void syncMigrations(String appId, String appPath) {
        String folderPath = appPath + ".migrations";
        ensureFolder(folderPath, "Migrations", "Applied data migration scripts");
        Set<String> expected = new HashSet<>();
        for (Map<String, Object> migration : dataStore.listMigrations(appId)) {
            String scriptId = String.valueOf(migration.get("script_id"));
            String nodeName = sanitizeNodeName(scriptId);
            expected.add(nodeName);
            ensureNode(
                    folderPath + "." + nodeName,
                    ObjectType.MIGRATION,
                    scriptId,
                    "version=" + migration.get("version"),
                    "application-migration-v1"
            );
        }
        pruneChildren(folderPath, expected);
    }

    private void syncOperatorScreens(String appId, String appPath) {
        String folderPath = appPath + ".screens";
        ensureFolder(folderPath, "Operator screens", "Screens from operator manifest");
        Set<String> expected = new HashSet<>();

        snapshotStore.findActive(appId).ifPresent(snapshot -> {
            if (snapshot.operatorManifestJson() == null || snapshot.operatorManifestJson().isBlank()) {
                return;
            }
            try {
                Map<String, Object> manifest = objectMapper.readValue(
                        snapshot.operatorManifestJson(),
                        new TypeReference<>() {
                        }
                );
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> screens = (List<Map<String, Object>>) manifest.get("screens");
                if (screens == null) {
                    return;
                }
                for (Map<String, Object> screen : screens) {
                    String screenId = String.valueOf(screen.get("id"));
                    String nodeName = sanitizeNodeName(screenId);
                    expected.add(nodeName);
                    String title = screen.get("title") != null
                            ? String.valueOf(screen.get("title"))
                            : screenId;
                    String kind = screen.containsKey("report")
                            ? "report screen"
                            : screen.containsKey("table")
                                    ? "table screen"
                                    : "screen";
                    ensureNode(
                            folderPath + "." + nodeName,
                            ObjectType.SCREEN,
                            title,
                            kind + ", id=" + screenId,
                            "operator-screen-v1"
                    );
                }
            } catch (Exception ignored) {
                // manifest parse errors should not break tree sync
            }
        });

        pruneChildren(folderPath, expected);
    }

    private void ensureFolder(String path, String title, String description) {
        ObjectType folderType = com.ispf.server.object.SystemObjectTypeResolver.resolve(path, "app-folder-v1")
                .orElse(ObjectType.CUSTOM);
        ensureNode(path, folderType, title, description, "app-folder-v1");
    }

    private void ensureNode(
            String path,
            ObjectType type,
            String displayName,
            String description,
            String templateId
    ) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.updateInfo(path, displayName, description != null ? description : "");
            objectManager.reconcileType(path, type);
            return;
        }
        int lastDot = path.lastIndexOf('.');
        if (lastDot > 0) {
            String parentPath = path.substring(0, lastDot);
            if (objectManager.tree().findByPath(parentPath).isEmpty()) {
                ensureNode(
                        parentPath,
                        com.ispf.server.object.SystemObjectTypeResolver.resolve(parentPath, "app-folder-v1")
                                .orElse(ObjectType.APPLICATION),
                        parentTitle(parentPath),
                        "",
                        "app-folder-v1"
                );
            }
        }
        String parentPath = lastDot > 0 ? path.substring(0, lastDot) : "";
        String name = path.substring(lastDot + 1);
        objectManager.create(
                parentPath,
                name,
                type,
                displayName,
                description != null ? description : "",
                templateId
        );
    }

    private void pruneChildren(String folderPath, Set<String> expectedChildNames) {
        if (objectManager.tree().findByPath(folderPath).isEmpty()) {
            return;
        }
        for (PlatformObject child : objectManager.tree().childrenOf(folderPath)) {
            String childName = child.path().substring(child.path().lastIndexOf('.') + 1);
            if (!expectedChildNames.contains(childName)) {
                objectManager.delete(child.path());
            }
        }
    }

    private static String functionNodeName(ApplicationFunctionHandler.DeployedFunction function) {
        String pathSuffix = function.objectPath().substring(function.objectPath().lastIndexOf('.') + 1);
        return sanitizeNodeName(function.functionName() + "__" + pathSuffix);
    }

    private static String bindingNodeName(ApplicationSqlBindingStore.SqlBinding binding) {
        String pathSuffix = binding.objectPath().substring(binding.objectPath().lastIndexOf('.') + 1);
        return sanitizeNodeName(binding.variableName() + "__" + pathSuffix);
    }

    private static String parentTitle(String path) {
        return switch (path) {
            case APPLICATIONS_ROOT -> "Applications";
            default -> path.substring(path.lastIndexOf('.') + 1);
        };
    }

    static String sanitizeNodeName(String name) {
        if (name == null || name.isBlank()) {
            return "node";
        }
        String sanitized = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        if (sanitized.isEmpty()) {
            return "node";
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            return "n_" + sanitized;
        }
        return sanitized;
    }
}
