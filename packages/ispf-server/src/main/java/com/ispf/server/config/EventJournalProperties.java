package com.ispf.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.event-journal")
public class EventJournalProperties {

    /**
     * When true, {@link com.ispf.server.event.EventJournalAsyncWriter} persists events on a background thread.
     * When false, events are written synchronously on the fire path.
     */
    private boolean asyncEnabled = true;
    private int queueCapacity = 10_000;
    private int batchSize = 200;
    private long flushIntervalMs = 100;
    private int recentCacheSize = 2000;
    private int writerThreads = 2;
    /** Platform default retention for event_history rows (Timescale policy, ClickHouse TTL, or app purge). */
    private int retentionDays = 90;
    /** {@code jdbc} (PostgreSQL/Timescale) or {@code clickhouse}. */
    private String store = "jdbc";
    private ClickHouse clickhouse = new ClickHouse();

    public static class ClickHouse {
        private String url = "http://localhost:8123";
        private String database = "ispf";
        private String table = "event_history";
        private String username = "default";
        private String password = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDatabase() {
            return database;
        }

        public void setDatabase(String database) {
            this.database = database;
        }

        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getFlushIntervalMs() {
        return flushIntervalMs;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
        this.flushIntervalMs = flushIntervalMs;
    }

    public int getRecentCacheSize() {
        return recentCacheSize;
    }

    public void setRecentCacheSize(int recentCacheSize) {
        this.recentCacheSize = recentCacheSize;
    }

    public int getWriterThreads() {
        return writerThreads;
    }

    public void setWriterThreads(int writerThreads) {
        this.writerThreads = writerThreads;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public void setRetentionDays(int retentionDays) {
        this.retentionDays = retentionDays;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public ClickHouse getClickhouse() {
        return clickhouse;
    }

    public void setClickhouse(ClickHouse clickhouse) {
        this.clickhouse = clickhouse;
    }

    public boolean isJdbcStore() {
        return !isClickHouseStore();
    }

    public boolean isClickHouseStore() {
        return "clickhouse".equalsIgnoreCase(store);
    }
}
