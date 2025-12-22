package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

public record InventoryReleaseCommandV1(
        String commandId,
        String occurredAt,
        String orderId
) {}
