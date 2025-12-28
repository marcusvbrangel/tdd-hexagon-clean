package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto;

public record PaymentCaptureCommandV1(
        String commandId,
        String occurredAt,
        String orderId,
        String paymentId
) {
}
