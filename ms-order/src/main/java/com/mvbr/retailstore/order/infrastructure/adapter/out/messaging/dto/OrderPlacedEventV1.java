package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto;

import java.util.List;

public record OrderPlacedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String customerId,
        List<ItemV1> items,
        String total,
        String currency
) {

    public record ItemV1(
            String productId,
            int quantity,
            String unitPrice
    ) { }
}
