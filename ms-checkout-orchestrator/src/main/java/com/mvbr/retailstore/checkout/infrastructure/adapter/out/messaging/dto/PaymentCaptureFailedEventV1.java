package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

public record PaymentCaptureFailedEventV1(
        String eventId,
        String occurredAt,
        String orderId,
        String paymentId,
        String providerPaymentId,
        String reason
) {
}
