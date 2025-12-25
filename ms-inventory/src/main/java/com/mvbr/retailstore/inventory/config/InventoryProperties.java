package com.mvbr.retailstore.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades do dominio de inventory (TTL de reserva e expiracao).
 */
@Component
@ConfigurationProperties(prefix = "inventory")
public class InventoryProperties {

    private final Reservation reservation = new Reservation();
    private final Expiration expiration = new Expiration();

    /**
     * Configuracoes relacionadas a criacao de reservas.
     */
    public Reservation getReservation() {
        return reservation;
    }

    /**
     * Configuracoes de expiracao automatica.
     */
    public Expiration getExpiration() {
        return expiration;
    }

    /**
     * Grupo de propriedades para tempo de vida da reserva.
     */
    public static class Reservation {
        private long ttlSeconds = 120;

        /**
         * TTL padrao em segundos.
         */
        public long getTtlSeconds() {
            return ttlSeconds;
        }

        /**
         * Ajuste do TTL via configuracao externa.
         */
        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
        }
    }

    /**
     * Grupo de propriedades para job de expiracao.
     */
    public static class Expiration {
        private long scanFixedDelayMs = 5000;
        private int batchSize = 50;

        /**
         * Intervalo do job de expiracao (em ms).
         */
        public long getScanFixedDelayMs() {
            return scanFixedDelayMs;
        }

        /**
         * Ajuste do intervalo via configuracao externa.
         */
        public void setScanFixedDelayMs(long scanFixedDelayMs) {
            this.scanFixedDelayMs = scanFixedDelayMs;
        }

        /**
         * Tamanho do lote de expiracao.
         */
        public int getBatchSize() {
            return batchSize;
        }

        /**
         * Ajuste do tamanho do lote via configuracao externa.
         */
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
