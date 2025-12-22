package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

public record PaymentDeclinedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String reason
) {}
