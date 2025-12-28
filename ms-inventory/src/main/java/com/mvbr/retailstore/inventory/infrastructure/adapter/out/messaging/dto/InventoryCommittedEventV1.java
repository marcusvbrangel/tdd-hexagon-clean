package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

public record InventoryCommittedEventV1(
        String eventId,
        String occurredAt,
        String orderId
) {
}
