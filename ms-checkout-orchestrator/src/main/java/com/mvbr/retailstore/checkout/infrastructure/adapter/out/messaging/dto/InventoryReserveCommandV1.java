package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

import java.util.List;

public record InventoryReserveCommandV1(
        String commandId,
        String occurredAt,
        String orderId,
        List<Line> lines
) {
    public record Line(String sku, int quantity) {}
}
