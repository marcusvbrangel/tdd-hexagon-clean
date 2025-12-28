package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto;

public record PaymentCapturedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String paymentId,
        String providerPaymentId
) {
}
