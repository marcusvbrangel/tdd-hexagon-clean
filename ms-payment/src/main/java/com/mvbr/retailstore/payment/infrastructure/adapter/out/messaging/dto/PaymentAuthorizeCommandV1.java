package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.dto;

public record PaymentAuthorizeCommandV1(
        String commandId,
        String occurredAt,
        String orderId,
        String customerId,
        String amount,
        String currency,
        String paymentMethod
) {
}
