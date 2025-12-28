package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

public record InventoryCommitCommandV1(
        String commandId,
        String occurredAt,
        String orderId
) {
}
