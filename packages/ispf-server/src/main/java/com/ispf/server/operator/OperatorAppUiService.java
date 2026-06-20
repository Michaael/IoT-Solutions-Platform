package com.ispf.server.operator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ispf.server.application.bundle.ApplicationBundleDeployService;
import com.ispf.server.application.data.ApplicationDataStore;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class OperatorAppUiService {

    private static final String PLATFORM_APP_ID = "platform";

    private final OperatorAppUiStore store;
    private final ApplicationDataStore applicationDataStore;
    private final ApplicationBundleDeployService bundleDeployService;
    private final OperatorAppObjectTreeService objectTreeService;
    private final ObjectMapper objectMapper;

    public OperatorAppUiService(
            OperatorAppUiStore store,
            ApplicationDataStore applicationDataStore,
            ApplicationBundleDeployService bundleDeployService,
            OperatorAppObjectTreeService objectTreeService,
            ObjectMapper objectMapper
    ) {
        this.store = store;
        this.applicationDataStore = applicationDataStore;
        this.bundleDeployService = bundleDeployService;
        this.objectTreeService = objectTreeService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureDefaults() throws Exception {
        if (store.findByAppId(PLATFORM_APP_ID).isPresent()) {
            return;
        }
        List<Map<String, String>> dashboards = List.of(
                Map.of(
                        "path", "root.platform.dashboards.snmp-host-monitoring",
                        "title", "SNMP Host Monitoring"
                ),
                Map.of(
                        "path", "root.platform.dashboards.demo-sensor",
                        "title", "Demo Sensor"
                )
        );
        store.upsert(new OperatorAppUiStore.OperatorAppUiRecord(
                PLATFORM_APP_ID,
                "Platform HMI",
                "root.platform.dashboards.snmp-host-monitoring",
                objectMapper.writeValueAsString(dashboards),
                java.time.Instant.now()
        ));
        objectTreeService.syncAll();
    }

    public List<Map<String, Object>> listApps() {
        Map<String, Map<String, Object>> byId = new TreeMap<>();
        for (OperatorAppUiStore.OperatorAppUiRecord record : store.listAll()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("appId", record.appId());
            entry.put("title", record.title());
            entry.put("source", "operator-apps");
            byId.put(record.appId(), entry);
        }
        for (Map<String, Object> app : applicationDataStore.listAllApps()) {
            String appId = String.valueOf(app.get("app_id"));
            if (byId.containsKey(appId) || !bundleDeployService.supportsOperatorUi(appId)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("appId", appId);
            entry.put("title", String.valueOf(app.get("display_name")));
            entry.put("source", "bundle");
            byId.put(appId, entry);
        }
        return new ArrayList<>(byId.values());
    }

    public Map<String, Object> createApp(String appId, String title) throws Exception {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("appId is required");
        }
        if (store.findByAppId(appId).isPresent()) {
            throw new IllegalArgumentException("Operator app already exists: " + appId);
        }
        String resolvedTitle = title != null && !title.isBlank() ? title.trim() : appId;
        store.upsert(new OperatorAppUiStore.OperatorAppUiRecord(
                appId,
                resolvedTitle,
                "",
                objectMapper.writeValueAsString(List.of()),
                java.time.Instant.now()
        ));
        objectTreeService.syncAll();
        return getUi(appId);
    }

    public Map<String, Object> getUi(String appId) throws Exception {
        OperatorAppUiStore.OperatorAppUiRecord record = store.findByAppId(appId)
                .orElseThrow(() -> new IllegalArgumentException("Operator app not found: " + appId));
        return toUiMap(record);
    }

    public Map<String, Object> saveUi(
            String appId,
            String title,
            String defaultDashboard,
            List<Map<String, String>> dashboards
    ) throws Exception {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (dashboards == null || dashboards.isEmpty()) {
            throw new IllegalArgumentException("dashboards must not be empty");
        }
        String resolvedDefault = defaultDashboard;
        if (resolvedDefault == null || resolvedDefault.isBlank()) {
            resolvedDefault = dashboards.get(0).get("path");
        }
        final String defaultPath = resolvedDefault;
        boolean defaultFound = dashboards.stream().anyMatch(item -> defaultPath.equals(item.get("path")));
        if (!defaultFound) {
            throw new IllegalArgumentException("defaultDashboard must be one of dashboards[].path");
        }
        store.upsert(new OperatorAppUiStore.OperatorAppUiRecord(
                appId,
                title.trim(),
                defaultPath,
                objectMapper.writeValueAsString(dashboards),
                java.time.Instant.now()
        ));
        objectTreeService.syncAll();
        return getUi(appId);
    }

    private Map<String, Object> toUiMap(OperatorAppUiStore.OperatorAppUiRecord record) throws Exception {
        List<Map<String, String>> dashboards = objectMapper.readValue(
                record.dashboardsJson(),
                new TypeReference<>() {
                }
        );
        Map<String, Object> ui = new LinkedHashMap<>();
        ui.put("appId", record.appId());
        ui.put("title", record.title());
        ui.put("defaultDashboard", record.defaultDashboard());
        ui.put("dashboards", dashboards);
        return ui;
    }
}
