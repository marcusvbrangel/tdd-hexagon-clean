package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto;

public record PaymentAuthorizedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String paymentId
) {
}
