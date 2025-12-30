package com.mvbr.retailstore.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox")
public class InventoryOutboxProperties {

    private final Relay relay = new Relay();
    private final Retention retention = new Retention();

    public Relay getRelay() {
        return relay;
    }

    public Retention getRetention() {
        return retention;
    }

    public static class Relay {

        private boolean enabled = true;

        private long fixedDelayMs = 10_000L;

        /**
         * Quantas mensagens por tick (limite).
         */
        private int batchSize = 100;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class Retention {

        private boolean enabled = true;

        private String cron = "0 0 3 * * *";

        /**
         * Quantos dias manter mensagens publicadas.
         */
        private int retentionDays = 7;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public int getRetentionDays() {
            return retentionDays;
        }

        public void setRetentionDays(int retentionDays) {
            this.retentionDays = retentionDays;
        }
    }
}
