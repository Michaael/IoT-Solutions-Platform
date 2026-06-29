package com.ispf.server.api;

import tools.jackson.databind.ObjectMapper;
import com.ispf.server.dashboard.DashboardService;
import com.ispf.server.federation.FederationProxyService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

    private final DashboardService dashboardService;
    private final FederationProxyService federationProxyService;
    private final ObjectMapper objectMapper;

    public DashboardController(
            DashboardService dashboardService,
            FederationProxyService federationProxyService,
            ObjectMapper objectMapper
    ) {
        this.dashboardService = dashboardService;
        this.federationProxyService = federationProxyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/by-path")
    public DashboardService.DashboardView get(@RequestParam String path) {
        return federationProxyService.resolve(path)
                .map(this::mapProxyDashboard)
                .orElseGet(() -> dashboardService.getDashboard(path));
    }

    @GetMapping("/by-path/context")
    public DashboardService.DashboardContextView getContext(@RequestParam String path) {
        return dashboardService.getContext(path);
    }

    @PutMapping("/by-path/context")
    public DashboardService.DashboardContextView saveContext(
            @RequestParam String path,
            @RequestBody SaveContextRequest request
    ) {
        return dashboardService.saveContext(path, request.context(), request.updatedBy());
    }

    private DashboardService.DashboardView mapProxyDashboard(FederationProxyService.FederationProxyTarget target) {
        var json = federationProxyService.proxyDashboard(target);
        String localPath = json.path("path").asText(target.localPath());
        String title = json.path("title").asText(localPath);
        int refreshIntervalMs = json.path("refreshIntervalMs").asInt(5000);
        String layoutJson = json.path("layoutJson").asText("");
        Object layout = json.hasNonNull("layout")
                ? objectMapper.convertValue(json.get("layout"), Object.class)
                : objectMapper.convertValue(json.path("layoutJson").asText("{}"), Object.class);
        return new DashboardService.DashboardView(localPath, title, refreshIntervalMs, layout, layoutJson);
    }

    @PutMapping("/by-path/layout")
    public DashboardService.DashboardView saveLayout(
            @RequestParam String path,
            @RequestBody SaveLayoutRequest request
    ) {
        return federationProxyService.resolve(path)
                .map(target -> federationProxyService.proxyDashboardSaveLayout(target, request.layoutJson()))
                .orElseGet(() -> dashboardService.saveLayout(path, request.layoutJson()));
    }

    @PutMapping("/by-path/title")
    public DashboardService.DashboardView saveTitle(
            @RequestParam String path,
            @RequestBody SaveTitleRequest request
    ) {
        return federationProxyService.resolve(path)
                .map(target -> federationProxyService.proxyDashboardSaveTitle(target, request.title()))
                .orElseGet(() -> dashboardService.updateTitle(path, request.title()));
    }

    public record SaveLayoutRequest(@NotBlank String layoutJson) {
    }

    public record SaveTitleRequest(@NotBlank String title) {
    }

    public record SaveContextRequest(
            java.util.Map<String, Object> context,
            String updatedBy
    ) {
    }
}
