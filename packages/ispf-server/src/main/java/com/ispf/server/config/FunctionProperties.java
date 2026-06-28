package com.ispf.server.config;

import com.ispf.server.application.function.FunctionAuditMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ispf.function")
public class FunctionProperties {

    private Audit audit = new Audit();

    public static class Audit {
        /** Master kill switch — when false, no function audit writes regardless of per-object flags. */
        private boolean enabled = true;
        /** {@code errors} or {@code all}. */
        private String mode = "errors";
        /** 0.0–1.0 sampling when enabled (1.0 = every matching record). */
        private double sampleRate = 1.0;
        private boolean asyncEnabled = true;
        private int queueCapacity = 10_000;
        private int batchSize = 100;
        private long flushIntervalMs = 200;
        private int retentionDays = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public FunctionAuditMode resolvedMode() {
            return FunctionAuditMode.parse(mode);
        }

        public double getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(double sampleRate) {
            this.sampleRate = sampleRate;
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

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }
    }

    public Audit getAudit() {
        return audit;
    }

    public void setAudit(Audit audit) {
        this.audit = audit != null ? audit : new Audit();
    }
}
