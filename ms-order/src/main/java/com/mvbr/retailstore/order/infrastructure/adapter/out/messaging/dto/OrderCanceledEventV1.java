package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto;

public record OrderCanceledEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String customerId
) {
}
