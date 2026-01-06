package com.mvbr.retailstore.notification.application.model;

public record FailedNotification(
        String commandId,
        String template,
        String orderId,
        String customerId,
        String recipient,
        String subject,
        String textBody,
        String htmlBody,
        int attempts
) {
}
