package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto;

public record OrderConfirmedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String customerId
) {
}
