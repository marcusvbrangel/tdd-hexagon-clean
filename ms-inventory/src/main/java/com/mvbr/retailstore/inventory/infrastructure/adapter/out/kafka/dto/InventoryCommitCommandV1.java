package com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.dto;

public record InventoryCommitCommandV1(
        String commandId,
        String occurredAt,
        String orderId
) {
}
