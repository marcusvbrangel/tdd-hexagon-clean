package com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto;

public record InventoryRejectedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String reason
) {
}
