package com.ispf.server.config;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

public final class IspfAuthorizationRules {

    private IspfAuthorizationRules() {
    }

    public static void apply(
            AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry auth
    ) {
        auth.requestMatchers(
                "/api/v1/info",
                "/api/v1/auth/login",
                "/api/v1/auth/me",
                "/actuator/health",
                "/actuator/prometheus",
                "/ws/**"
        ).permitAll();

        auth.requestMatchers("/api/v1/alert-rules/**", "/api/v1/correlators/**")
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/work-queue/**")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/objects/by-path/functions/invoke")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(HttpMethod.POST, "/api/v1/bff/**")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(new AntPathRequestMatcher("/api/v1/applications/*/operator-ui", "GET"))
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);
        auth.requestMatchers(new AntPathRequestMatcher("/api/v1/applications/*/hmi-ui", "GET"))
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);
        auth.requestMatchers(new AntPathRequestMatcher("/api/v1/applications/*/operator-manifest", "GET"))
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers(new AntPathRequestMatcher("/api/v1/operator-apps", "GET"))
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);
        auth.requestMatchers(new AntPathRequestMatcher("/api/v1/operator-apps/*/ui", "GET"))
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);
        auth.requestMatchers(HttpMethod.POST, "/api/v1/operator-apps/**")
                .hasRole(IspfRoles.ADMIN);
        auth.requestMatchers(new AntPathRequestMatcher("/api/v1/operator-apps/**", "PUT"))
                .hasRole(IspfRoles.ADMIN);

        auth.requestMatchers(new AntPathRequestMatcher("/api/v1/**", "GET"))
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

        auth.requestMatchers(HttpMethod.POST, "/api/v1/applications/*/reports/*/run")
                .hasAnyRole(IspfRoles.OPERATOR, IspfRoles.ADMIN);

        auth.requestMatchers("/api/v1/**").hasRole(IspfRoles.ADMIN);
    }
}
