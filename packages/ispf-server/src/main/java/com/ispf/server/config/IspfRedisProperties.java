package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ispf.redis")
public class IspfRedisProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 6379;
    private String password = "";
    private int database = 0;
    private Duration timeout = Duration.ofSeconds(2);
    private boolean correlatorWindowsEnabled = false;
    private Cache cache = new Cache();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public int getDatabase() {
        return database;
    }

    public void setDatabase(int database) {
        this.database = database;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public boolean isCorrelatorWindowsEnabled() {
        return correlatorWindowsEnabled;
    }

    public void setCorrelatorWindowsEnabled(boolean correlatorWindowsEnabled) {
        this.correlatorWindowsEnabled = correlatorWindowsEnabled;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public static class Cache {

        private Duration contextPackTtl = Duration.ofHours(1);
        private Duration platformBriefingTtl = Duration.ofMinutes(5);
        private Duration objectAclTtl = Duration.ofMinutes(10);

        public Duration getContextPackTtl() {
            return contextPackTtl;
        }

        public void setContextPackTtl(Duration contextPackTtl) {
            this.contextPackTtl = contextPackTtl;
        }

        public Duration getPlatformBriefingTtl() {
            return platformBriefingTtl;
        }

        public void setPlatformBriefingTtl(Duration platformBriefingTtl) {
            this.platformBriefingTtl = platformBriefingTtl;
        }

        public Duration getObjectAclTtl() {
            return objectAclTtl;
        }

        public void setObjectAclTtl(Duration objectAclTtl) {
            this.objectAclTtl = objectAclTtl;
        }
    }
}
