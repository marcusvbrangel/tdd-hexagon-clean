package com.mvbr.retailstore.order.infrastructure.observability;

import com.mvbr.retailstore.order.domain.event.DomainEvent;
import com.mvbr.retailstore.order.domain.event.OrderPlacedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

@Component
public class OrderBusinessMetrics {

    private final MeterRegistry registry;
    private final DistributionSummary orderItems;

    public OrderBusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.orderItems = DistributionSummary.builder("business_order_items")
                .register(registry);
    }

    public void record(DomainEvent event) {
        if (event == null) {
            return;
        }

        Counter.builder("business_orders_total")
                .tag("event", event.eventType())
                .register(registry)
                .increment();

        if (event instanceof OrderPlacedEvent placedEvent) {
            recordOrderPlaced(placedEvent);
        }
    }

    private void recordOrderPlaced(OrderPlacedEvent event) {
        double total = parseAmount(event.total());
        if (total > 0) {
            orderValue(event.currency()).record(total);
        }

        long items = event.items().stream()
                .mapToLong(OrderPlacedEvent.Item::quantity)
                .sum();
        if (items > 0) {
            orderItems.record(items);
        }
    }

    private DistributionSummary orderValue(String currency) {
        String label = currency == null ? "unknown" : currency.trim().toUpperCase(Locale.ROOT);
        if (label.isBlank()) {
            label = "unknown";
        }
        return DistributionSummary.builder("business_order_value")
                .tag("currency", label)
                .register(registry);
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
