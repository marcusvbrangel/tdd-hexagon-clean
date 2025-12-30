package com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto;

import java.util.List;

public record InventoryReservedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String expiresAt,
        List<Item> items
) {

    public record Item(String productId, long quantity) {
    }
}
