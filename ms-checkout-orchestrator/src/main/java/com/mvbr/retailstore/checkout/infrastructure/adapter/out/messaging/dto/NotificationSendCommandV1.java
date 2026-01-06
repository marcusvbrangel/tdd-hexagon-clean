package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

import java.util.List;
import java.util.Map;

public record NotificationSendCommandV1(
        String commandId,
        String occurredAt,
        String orderId,
        String customerId,
        String template,
        List<String> channels,
        Map<String, Object> data
) {
}
