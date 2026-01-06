package com.mvbr.retailstore.notification.application.command;

import java.util.List;
import java.util.Map;

public record SendNotificationCommand(
        String commandId,
        String occurredAt,
        String orderId,
        String customerId,
        String recipient,
        String template,
        List<String> channels,
        Map<String, Object> data
) {
}
