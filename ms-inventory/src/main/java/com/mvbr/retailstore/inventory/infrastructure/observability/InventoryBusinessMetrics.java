package com.mvbr.retailstore.inventory.infrastructure.observability;

import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto.InventoryReservedEventV1;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InventoryBusinessMetrics {

    private final MeterRegistry registry;
    private final DistributionSummary reservedItems;

    public InventoryBusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.reservedItems = DistributionSummary.builder("business_inventory_items")
                .tag("event", "inventory.reserved")
                .register(registry);
    }

    public void record(String eventType, Object payload) {
        if (eventType == null || eventType.isBlank()) {
            return;
        }

        Counter.builder("business_inventory_total")
                .tag("event", eventType)
                .register(registry)
                .increment();

        if (payload instanceof InventoryReservedEventV1 reservedEvent) {
            long items = reservedEvent.items().stream()
                    .mapToLong(InventoryReservedEventV1.Item::quantity)
                    .sum();
            if (items > 0) {
                reservedItems.record(items);
            }
        }
    }
}
