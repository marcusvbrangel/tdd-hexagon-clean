package com.mvbr.retailstore.notification.application.service;

import com.mvbr.retailstore.notification.application.model.FailedNotification;
import com.mvbr.retailstore.notification.application.model.NotificationMessage;
import com.mvbr.retailstore.notification.application.model.NotificationTemplate;
import com.mvbr.retailstore.notification.application.port.out.CustomerClient;
import com.mvbr.retailstore.notification.application.port.out.EmailSender;
import com.mvbr.retailstore.notification.application.port.out.SentNotificationRepository;
import com.mvbr.retailstore.notification.config.NotificationProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

@Service
public class NotificationRetryService {

    private static final Logger log = Logger.getLogger(NotificationRetryService.class.getName());

    private final SentNotificationRepository sentNotificationRepository;
    private final CustomerClient customerClient;
    private final EmailSender emailSender;
    private final NotificationProperties properties;

    public NotificationRetryService(SentNotificationRepository sentNotificationRepository,
                                    CustomerClient customerClient,
                                    EmailSender emailSender,
                                    NotificationProperties properties) {
        this.sentNotificationRepository = sentNotificationRepository;
        this.customerClient = customerClient;
        this.emailSender = emailSender;
        this.properties = properties;
    }

    public void retryFailed() {
        int batchSize = properties.getRetry().getBatchSize();
        List<FailedNotification> failed = sentNotificationRepository.findFailedForRetry(batchSize);
        if (failed.isEmpty()) {
            return;
        }

        for (FailedNotification entry : failed) {
            if (isMaxAttemptsReached(entry.attempts())) {
                sentNotificationRepository.markIgnored(entry.commandId(), "MAX_ATTEMPTS_EXCEEDED");
                continue;
            }
            if (!sentNotificationRepository.markRetrying(entry.commandId())) {
                continue;
            }

            String subject = entry.subject();
            String textBody = entry.textBody();
            String htmlBody = entry.htmlBody();
            if (subject == null || subject.isBlank()
                    || textBody == null || textBody.isBlank()
                    || htmlBody == null || htmlBody.isBlank()) {
                NotificationMessage rebuilt = rebuildMessage(entry);
                if (rebuilt != null) {
                    subject = rebuilt.subject();
                    textBody = rebuilt.textBody();
                    htmlBody = rebuilt.htmlBody();
                    sentNotificationRepository.storeContent(entry.commandId(), subject, textBody, htmlBody);
                } else if (textBody != null && !textBody.isBlank() && subject != null && !subject.isBlank()) {
                    htmlBody = toHtmlFallback(textBody);
                    sentNotificationRepository.storeContent(entry.commandId(), subject, textBody, htmlBody);
                } else {
                    sentNotificationRepository.markFailed(entry.commandId(), entry.recipient(), "MISSING_CONTENT");
                    if (isMaxAttemptsReached(entry.attempts() + 1)) {
                        sentNotificationRepository.markIgnored(entry.commandId(), "MAX_ATTEMPTS_EXCEEDED");
                    }
                    log.warning("Retry skipped: missing content commandId=" + entry.commandId());
                    continue;
                }
            }

            String recipient = resolveRecipient(entry);
            if (recipient == null || recipient.isBlank()) {
                sentNotificationRepository.markFailed(entry.commandId(), null, "RECIPIENT_EMAIL_NOT_FOUND");
                if (isMaxAttemptsReached(entry.attempts() + 1)) {
                    sentNotificationRepository.markIgnored(entry.commandId(), "MAX_ATTEMPTS_EXCEEDED");
                }
                log.warning("Retry skipped: missing recipient commandId=" + entry.commandId());
                continue;
            }

            try {
                emailSender.send(recipient, subject, textBody, htmlBody);
                sentNotificationRepository.markSent(entry.commandId(), recipient);
                log.info("Retry notification sent commandId=" + entry.commandId() + " to=" + recipient);
            } catch (RuntimeException ex) {
                sentNotificationRepository.markFailed(entry.commandId(), recipient, ex.getMessage());
                if (isMaxAttemptsReached(entry.attempts() + 1)) {
                    sentNotificationRepository.markIgnored(entry.commandId(), "MAX_ATTEMPTS_EXCEEDED");
                }
                log.severe("Retry notification failed commandId=" + entry.commandId() + " error=" + ex.getMessage());
            }
        }
    }

    private String resolveRecipient(FailedNotification entry) {
        if (entry.recipient() != null && !entry.recipient().isBlank()) {
            return entry.recipient();
        }
        if (entry.customerId() == null || entry.customerId().isBlank()) {
            return null;
        }
        return customerClient.findEmailById(entry.customerId()).orElse(null);
    }

    private NotificationMessage rebuildMessage(FailedNotification entry) {
        if (entry.template() == null || entry.template().isBlank()) {
            return null;
        }
        if (entry.orderId() == null || entry.orderId().isBlank()) {
            return null;
        }
        Optional<NotificationTemplate> template = NotificationTemplate.from(entry.template());
        if (template.isEmpty()) {
            return null;
        }
        return template.get().render(entry.orderId(), Map.of());
    }

    private boolean isMaxAttemptsReached(int attempts) {
        int maxAttempts = properties.getRetry().getMaxAttempts();
        return maxAttempts > 0 && attempts >= maxAttempts;
    }

    private String toHtmlFallback(String textBody) {
        String escaped = textBody
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("\n", "<br>");
        return "<!DOCTYPE html><html><body style=\"font-family: Arial, sans-serif;\">" +
                escaped +
                "</body></html>";
    }
}
