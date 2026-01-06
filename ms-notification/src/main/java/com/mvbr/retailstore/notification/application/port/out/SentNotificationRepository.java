package com.mvbr.retailstore.notification.application.port.out;

import com.mvbr.retailstore.notification.application.model.FailedNotification;

import java.util.List;

public interface SentNotificationRepository {
    boolean markPendingIfFirst(String commandId, String template, String orderId, String customerId);

    void storeContent(String commandId, String subject, String textBody, String htmlBody);

    void markSent(String commandId, String recipient);

    void markFailed(String commandId, String recipient, String error);

    boolean markRetrying(String commandId);

    void markIgnored(String commandId, String reason);

    List<FailedNotification> findFailedForRetry(int limit);
}
