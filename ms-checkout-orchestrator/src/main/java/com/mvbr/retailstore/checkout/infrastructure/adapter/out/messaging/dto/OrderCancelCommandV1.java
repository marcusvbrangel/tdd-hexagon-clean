package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

public record OrderCancelCommandV1(
        String commandId,
        String occurredAt,
        String orderId,
        String reason
) {}
