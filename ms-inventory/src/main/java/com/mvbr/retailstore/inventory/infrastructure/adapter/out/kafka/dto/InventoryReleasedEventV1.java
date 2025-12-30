package com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto;

public record InventoryReleasedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String reason
) {
}
