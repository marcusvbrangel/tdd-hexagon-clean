package com.mvbr.retailstore.payment.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentBusinessMetrics {

    private final MeterRegistry registry;

    public PaymentBusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return;
        }
        Counter.builder("business_payments_total")
                .tag("event", eventType)
                .register(registry)
                .increment();
    }
}
