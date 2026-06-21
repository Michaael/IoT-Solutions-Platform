package com.ispf.server.api;

import com.ispf.server.federation.FederationSecretsKeyService;
import com.ispf.server.platform.update.PlatformVersionSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
public class PlatformInfoController {

    private final Optional<BuildProperties> buildProperties;
    private final FederationSecretsKeyService secretsKeyService;
    private final String environment;

    public PlatformInfoController(
            Optional<BuildProperties> buildProperties,
            FederationSecretsKeyService secretsKeyService,
            @Value("${ispf.environment:dev}") String environment
    ) {
        this.buildProperties = buildProperties;
        this.secretsKeyService = secretsKeyService;
        this.environment = environment;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "IoT Solutions Platform Framework");
        payload.put("shortName", "ISPF");
        payload.put("version", PlatformVersionSupport.currentVersion(buildProperties));
        payload.put("environment", environment);
        payload.put("timestamp", Instant.now().toString());
        payload.put("javaVersion", Runtime.version().toString());
        payload.put("springBootVersion", SpringBootVersion.getVersion());
        payload.put("capabilities", new String[]{
                "object-tree",
                "typed-data-records",
                "cel-expressions",
                "device-drivers",
                "rest-api",
                "websocket-events",
                "dashboard-builder",
                "workflow-engine",
                "federation",
                "federation-issue-token",
                "federation-remote-token",
                "federation-auth-refresh",
                "federation-tunnel",
                "federation-secrets-key",
                "oidc-rbac",
                "collaboration-revision",
                "collaboration-presence",
                "collaboration-change-sets"
        });
        payload.put("federationSecretsKeyConfigured", secretsKeyService.isConfigured());
        payload.put("federationSecretsKeySource", secretsKeyService.source().name());
        return payload;
    }
}
