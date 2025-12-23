package com.mvbr.retailstore.checkout.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "saga")
public class SagaProperties {

    private final Timeouts timeouts = new Timeouts();
    private final Retries retries = new Retries();

    public Timeouts getTimeouts() {
        return timeouts;
    }

    public Retries getRetries() {
        return retries;
    }

    public static class Timeouts {
        private long inventorySeconds = 30;
        private long paymentSeconds = 120;
        private long orderCompleteSeconds = 60;

        public long getInventorySeconds() {
            return inventorySeconds;
        }

        public void setInventorySeconds(long inventorySeconds) {
            this.inventorySeconds = inventorySeconds;
        }

        public long getPaymentSeconds() {
            return paymentSeconds;
        }

        public void setPaymentSeconds(long paymentSeconds) {
            this.paymentSeconds = paymentSeconds;
        }

        public long getOrderCompleteSeconds() {
            return orderCompleteSeconds;
        }

        public void setOrderCompleteSeconds(long orderCompleteSeconds) {
            this.orderCompleteSeconds = orderCompleteSeconds;
        }
    }

    public static class Retries {
        private int inventoryMax = 2;
        private int paymentMax = 3;
        private int orderCompleteMax = 2;

        public int getInventoryMax() {
            return inventoryMax;
        }

        public void setInventoryMax(int inventoryMax) {
            this.inventoryMax = inventoryMax;
        }

        public int getPaymentMax() {
            return paymentMax;
        }

        public void setPaymentMax(int paymentMax) {
            this.paymentMax = paymentMax;
        }

        public int getOrderCompleteMax() {
            return orderCompleteMax;
        }

        public void setOrderCompleteMax(int orderCompleteMax) {
            this.orderCompleteMax = orderCompleteMax;
        }
    }
}
