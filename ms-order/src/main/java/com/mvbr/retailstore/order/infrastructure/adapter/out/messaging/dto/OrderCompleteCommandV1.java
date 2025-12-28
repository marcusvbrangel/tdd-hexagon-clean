package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto;

public record OrderCompleteCommandV1(
        String commandId,
        String occurredAt,
        String orderId
) { }
