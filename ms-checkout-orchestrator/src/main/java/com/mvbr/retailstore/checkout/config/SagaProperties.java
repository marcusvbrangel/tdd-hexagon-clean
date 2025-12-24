package com.mvbr.retailstore.checkout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "saga")
/**
 * Propriedades configuraveis da saga (timeouts e retries).
 * Lidas do application.yaml.
 */
public class SagaProperties {

    private final Timeouts timeouts = new Timeouts();
    private final Retries retries = new Retries();

    /**
     * Agrupa tempos limite das etapas.
     */
    public Timeouts getTimeouts() {
        return timeouts;
    }

    /**
     * Agrupa limites de retry das etapas.
     */
    public Retries getRetries() {
        return retries;
    }

    /**
     * Configuracoes de timeout por etapa.
     */
    public static class Timeouts {
        private long inventorySeconds = 30;
        private long paymentSeconds = 120;
        private long orderCompleteSeconds = 60;

        /**
         * Timeout da reserva de estoque.
         */
        public long getInventorySeconds() {
            return inventorySeconds;
        }

        /**
         * Ajusta timeout da reserva de estoque.
         */
        public void setInventorySeconds(long inventorySeconds) {
            this.inventorySeconds = inventorySeconds;
        }

        /**
         * Timeout da autorizacao de pagamento.
         */
        public long getPaymentSeconds() {
            return paymentSeconds;
        }

        /**
         * Ajusta timeout da autorizacao de pagamento.
         */
        public void setPaymentSeconds(long paymentSeconds) {
            this.paymentSeconds = paymentSeconds;
        }

        /**
         * Timeout para concluir o pedido.
         */
        public long getOrderCompleteSeconds() {
            return orderCompleteSeconds;
        }

        /**
         * Ajusta timeout para concluir o pedido.
         */
        public void setOrderCompleteSeconds(long orderCompleteSeconds) {
            this.orderCompleteSeconds = orderCompleteSeconds;
        }
    }

    /**
     * Configuracoes de quantidade maxima de tentativas.
     */
    public static class Retries {
        private int inventoryMax = 2;
        private int paymentMax = 3;
        private int orderCompleteMax = 2;

        /**
         * Numero maximo de tentativas para reserva de estoque.
         */
        public int getInventoryMax() {
            return inventoryMax;
        }

        /**
         * Ajusta numero maximo de tentativas para reserva de estoque.
         */
        public void setInventoryMax(int inventoryMax) {
            this.inventoryMax = inventoryMax;
        }

        /**
         * Numero maximo de tentativas para autorizacao de pagamento.
         */
        public int getPaymentMax() {
            return paymentMax;
        }

        /**
         * Ajusta numero maximo de tentativas para autorizacao de pagamento.
         */
        public void setPaymentMax(int paymentMax) {
            this.paymentMax = paymentMax;
        }

        /**
         * Numero maximo de tentativas para conclusao do pedido.
         */
        public int getOrderCompleteMax() {
            return orderCompleteMax;
        }

        /**
         * Ajusta numero maximo de tentativas para conclusao do pedido.
         */
        public void setOrderCompleteMax(int orderCompleteMax) {
            this.orderCompleteMax = orderCompleteMax;
        }
    }
}
