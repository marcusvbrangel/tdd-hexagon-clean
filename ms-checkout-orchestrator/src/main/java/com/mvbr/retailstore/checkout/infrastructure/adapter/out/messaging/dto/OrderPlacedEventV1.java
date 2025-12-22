package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

import java.util.List;

public record OrderPlacedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String customerId,
        List<Item> items,
        String discount
) {
    public record Item(String productId, int quantity, String unitPrice) {}
}
