package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

import java.util.List;

public record InventoryReserveCommandV1(
        String commandId,
        String occurredAt,
        String orderId,
        List<Item> items
) {
    public record Item(String productId, int quantity) {}
}
