package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto;

import java.util.List;

public record OrderPlacedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String customerId,
        List<String> productIds
) {
}
