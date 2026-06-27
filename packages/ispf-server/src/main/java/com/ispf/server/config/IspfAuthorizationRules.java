package com.ispf.server.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

public final class IspfAuthorizationRules {

    private IspfAuthorizationRules() {
    }

    public static void apply(
            AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth.requestMatchers(
                "/api/v1/info",
                "/api/v1/auth/login",
                "/api/v1/auth/config",
                "/api/v1/auth/me",
                "/actuator/health",
                "/ws/**"
        ).permitAll();

        auth.requestMatchers("/actuator/prometheus")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/platform/metrics", "/api/v1/platform/function-invocations",
                        "/api/v1/platform/installation-id", "/api/v1/platform/automation-index/**",
                        "/api/v1/platform/runtime-settings")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/ai/**")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/platform/update/**")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/alert-rules/**", "/api/v1/correlators/**",
                        "/api/v1/data-sources/**", "/api/v1/migrations/**", "/api/v1/sql-bindings/**")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/work-queue/**")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/objects/by-path/functions/invoke")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/bff/**")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.GET, "/api/v1/applications/*/operator-ui")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);
        auth.requestMatchers(HttpMethod.GET, "/api/v1/applications/*/hmi-ui")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);
        auth.requestMatchers(HttpMethod.GET, "/api/v1/applications/*/operator-manifest")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.GET, "/api/v1/operator-apps")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);
        auth.requestMatchers(HttpMethod.GET, "/api/v1/operator-apps/*/ui")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);
        auth.requestMatchers(HttpMethod.POST, "/api/v1/operator-apps/**")
                .hasRole(IspfRoles.ADMIN);
        auth.requestMatchers(HttpMethod.PUT, "/api/v1/operator-apps/**")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.GET, "/api/v1/**")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/events/**")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/workflows/by-path/run")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/workflows/instances/*/cancel")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/workflows/instances/*/signal")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/workflows/signal")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/security/**")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/federation/**")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/tenants/**")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/applications/*/reports/*/run")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/reports/by-path/run")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        // Object variable writes: operators allowed at HTTP layer; per-object ACL enforced in ObjectController.
        auth.requestMatchers(HttpMethod.PUT, "/api/v1/objects/by-path/variables")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);
        auth.requestMatchers(HttpMethod.PATCH, "/api/v1/objects/by-path/variables/**")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/**").hasRole(IspfRoles.ADMIN);
    }
}
