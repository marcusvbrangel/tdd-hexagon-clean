package com.mvbr.retailstore.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "inventory.inbox")
public class InventoryInboxProperties {

    private final Backoff backoff = new Backoff();
    private final Recovery recovery = new Recovery();

    public Backoff getBackoff() {
        return backoff;
    }

    public Recovery getRecovery() {
        return recovery;
    }

    public static class Backoff {

        /**
         * Base do lease (tentativa 1). Ex: PT10S
         */
        private Duration baseLease = Duration.ofSeconds(10);

        /**
         * Máximo do lease (cap). Ex: PT10M
         */
        private Duration maxLease = Duration.ofMinutes(10);

        /**
         * Lease usado para falhas BUSINESS/POISON (para "não retryar").
         * Ex: P3650D (~10 anos)
         */
        private Duration noRetryLease = Duration.ofDays(3650);

        public Duration getBaseLease() {
            return baseLease;
        }

        public void setBaseLease(Duration baseLease) {
            this.baseLease = baseLease;
        }

        public Duration getMaxLease() {
            return maxLease;
        }

        public void setMaxLease(Duration maxLease) {
            this.maxLease = maxLease;
        }

        public Duration getNoRetryLease() {
            return noRetryLease;
        }

        public void setNoRetryLease(Duration noRetryLease) {
            this.noRetryLease = noRetryLease;
        }
    }

    public static class Recovery {

        /**
         * Delay entre execuções do recovery. Ex: 5000
         */
        private long delayMs = 5000;

        /**
         * Batch por ciclo (quantos inbox pega por vez)
         */
        private int batchSize = 100;

        public long getDelayMs() {
            return delayMs;
        }

        public void setDelayMs(long delayMs) {
            this.delayMs = delayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
