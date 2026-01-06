package com.mvbr.retailstore.notification.application.service;

import com.mvbr.retailstore.notification.application.command.SendNotificationCommand;
import com.mvbr.retailstore.notification.application.command.SagaContext;
import com.mvbr.retailstore.notification.application.model.NotificationMessage;
import com.mvbr.retailstore.notification.application.model.NotificationTemplate;
import com.mvbr.retailstore.notification.application.port.in.SendNotificationUseCase;
import com.mvbr.retailstore.notification.application.port.out.CustomerClient;
import com.mvbr.retailstore.notification.application.port.out.EmailSender;
import com.mvbr.retailstore.notification.application.port.out.SentNotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

@Service
public class NotificationCommandService implements SendNotificationUseCase {

    private static final Logger log = Logger.getLogger(NotificationCommandService.class.getName());

    private final SentNotificationRepository sentNotificationRepository;
    private final CustomerClient customerClient;
    private final EmailSender emailSender;

    public NotificationCommandService(SentNotificationRepository sentNotificationRepository,
                                      CustomerClient customerClient,
                                      EmailSender emailSender) {
        this.sentNotificationRepository = sentNotificationRepository;
        this.customerClient = customerClient;
        this.emailSender = emailSender;
    }

    @Override
    public void send(SendNotificationCommand command, SagaContext sagaContext) {
        if (command == null) {
            return;
        }
        if (command.commandId() == null || command.commandId().isBlank()) {
            throw new IllegalArgumentException("commandId is required");
        }
        if (command.orderId() == null || command.orderId().isBlank()) {
            log.warning("Skipping notification without orderId. commandId=" + command.commandId());
            return;
        }
        if (command.template() == null || command.template().isBlank()) {
            log.warning("Skipping notification without template. commandId=" + command.commandId());
            return;
        }
        if (!hasEmailChannel(command.channels())) {
            log.info("Skipping notification without EMAIL channel. commandId=" + command.commandId());
            return;
        }

        boolean first = sentNotificationRepository.markPendingIfFirst(
                command.commandId(),
                command.template(),
                command.orderId(),
                command.customerId()
        );
        if (!first) {
            log.info("Duplicate notification commandId=" + command.commandId());
            return;
        }

        Optional<NotificationTemplate> template = NotificationTemplate.from(command.template());
        if (template.isEmpty()) {
            markFailed(command, null, "UNKNOWN_TEMPLATE");
            return;
        }

        NotificationMessage message = template.get().render(command.orderId(), command.data());
        sentNotificationRepository.storeContent(
                command.commandId(),
                message.subject(),
                message.textBody(),
                message.htmlBody()
        );

        String recipient = resolveRecipient(command);
        if (recipient == null || recipient.isBlank()) {
            markFailed(command, null, "RECIPIENT_EMAIL_NOT_FOUND");
            return;
        }

        try {
            emailSender.send(recipient, message.subject(), message.textBody(), message.htmlBody());
            sentNotificationRepository.markSent(command.commandId(), recipient);
            log.info("Notification sent commandId=" + command.commandId() + " to=" + recipient);
        } catch (RuntimeException ex) {
            markFailed(command, recipient, ex.getMessage());
            log.severe("Notification failed commandId=" + command.commandId() + " error=" + ex.getMessage());
        }
    }

    private boolean hasEmailChannel(List<String> channels) {
        if (channels == null || channels.isEmpty()) {
            return false;
        }
        return channels.stream().anyMatch(channel -> "EMAIL".equalsIgnoreCase(channel));
    }

    private String resolveRecipient(SendNotificationCommand command) {
        if (command.recipient() != null && !command.recipient().isBlank()) {
            return command.recipient();
        }
        if (command.customerId() == null || command.customerId().isBlank()) {
            return null;
        }
        return customerClient.findEmailById(command.customerId()).orElse(null);
    }

    private void markFailed(SendNotificationCommand command, String recipient, String reason) {
        sentNotificationRepository.markFailed(command.commandId(), recipient, reason);
    }
}
