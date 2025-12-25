package com.mvbr.retailstore.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "inventory")
public class InventoryProperties {

    private final Reservation reservation = new Reservation();
    private final Expiration expiration = new Expiration();

    public Reservation getReservation() {
        return reservation;
    }

    public Expiration getExpiration() {
        return expiration;
    }

    public static class Reservation {
        private long ttlSeconds = 120;

        public long getTtlSeconds() {
            return ttlSeconds;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    public static class Expiration {
        private long scanFixedDelayMs = 5000;
        private int batchSize = 50;

        public long getScanFixedDelayMs() {
            return scanFixedDelayMs;
        }

        public void setScanFixedDelayMs(long scanFixedDelayMs) {
            this.scanFixedDelayMs = scanFixedDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
