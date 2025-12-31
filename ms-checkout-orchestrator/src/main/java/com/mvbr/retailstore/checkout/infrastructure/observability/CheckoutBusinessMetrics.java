package com.mvbr.retailstore.checkout.infrastructure.observability;

import com.mvbr.retailstore.checkout.domain.model.SagaStep;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
public class CheckoutBusinessMetrics {

    private final MeterRegistry registry;

    public CheckoutBusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordOrderPlaced(OrderPlacedEventV1 event) {
        if (event == null) {
            return;
        }
        String paymentMethod = normalizeTag(event.paymentMethod(), "unknown");
        String discounted = hasDiscount(event.discount()) ? "true" : "false";

        Counter.builder("business_checkout_orders_total")
                .tag("payment_method", paymentMethod)
                .tag("discounted", discounted)
                .register(registry)
                .increment();

        double discount = parseAmount(event.discount());
        if (discount > 0) {
            discountValue(event.currency()).record(discount);
        }
    }

    public void recordSagaOutcome(String outcome, String reason) {
        String outcomeTag = normalizeTag(outcome, "unknown");
        String reasonTag = normalizeTag(reason, "none");
        Counter.builder("business_saga_outcomes_total")
                .tag("outcome", outcomeTag)
                .tag("reason", reasonTag)
                .register(registry)
                .increment();
    }

    public void recordTimeout(SagaStep step) {
        Counter.builder("business_saga_timeouts_total")
                .tag("step", step == null ? "unknown" : step.name())
                .register(registry)
                .increment();
    }

    public void recordRetry(SagaStep step) {
        Counter.builder("business_saga_retries_total")
                .tag("step", step == null ? "unknown" : step.name())
                .register(registry)
                .increment();
    }

    private DistributionSummary discountValue(String currency) {
        String label = normalizeCurrency(currency);
        return DistributionSummary.builder("business_order_discount_value")
                .tag("currency", label)
                .register(registry);
    }

    private boolean hasDiscount(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        double value = parseAmount(raw);
        return value > 0 || raw.trim().length() > 0;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null) {
            return "UNKNOWN";
        }
        String value = currency.trim().toUpperCase(Locale.ROOT);
        return value.isBlank() ? "UNKNOWN" : value;
    }

    private String normalizeTag(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        return normalized.isBlank() ? fallback : normalized;
    }

    private double parseAmount(String raw) {
        if (raw == null) {
            return 0;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return 0;
        }
        if (value.contains(",") && !value.contains(".")) {
            value = value.replace(',', '.');
        }
        try {
            return new BigDecimal(value).doubleValue();
        } catch (Exception ex) {
            return 0;
        }
    }
}
