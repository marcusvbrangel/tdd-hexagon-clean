package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.dto;

public record InventoryReleaseCommandV1(
        String commandId,
        String occurredAt,
        String orderId,
        String reason
) {
}
