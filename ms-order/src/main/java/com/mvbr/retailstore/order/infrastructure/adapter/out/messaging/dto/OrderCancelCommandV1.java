package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto;

public record OrderCancelCommandV1(
        String commandId,
        String occurredAt,
        String orderId,
        String reason
) { }
