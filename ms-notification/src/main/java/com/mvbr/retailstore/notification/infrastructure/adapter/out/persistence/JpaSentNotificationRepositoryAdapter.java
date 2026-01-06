package com.mvbr.retailstore.notification.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.notification.application.model.FailedNotification;
import com.mvbr.retailstore.notification.application.port.out.SentNotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class JpaSentNotificationRepositoryAdapter implements SentNotificationRepository {

    private final JpaSentNotificationSpringDataRepository repository;

    public JpaSentNotificationRepositoryAdapter(JpaSentNotificationSpringDataRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean markPendingIfFirst(String commandId, String template, String orderId, String customerId) {
        try {
            JpaSentNotificationEntity entity = new JpaSentNotificationEntity(
                    commandId,
                    template,
                    orderId,
                    customerId,
                    JpaSentNotificationEntity.Status.PENDING.name(),
                    Instant.now()
            );
            repository.save(entity);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }

    @Override
    public void storeContent(String commandId, String subject, String textBody, String htmlBody) {
        JpaSentNotificationEntity entity = repository.findById(commandId)
                .orElseThrow(() -> new IllegalStateException("Notification not reserved commandId=" + commandId));
        entity.setSubject(subject);
        entity.setBody(textBody);
        entity.setBodyHtml(htmlBody);
        repository.save(entity);
    }

    @Override
    public void markSent(String commandId, String recipient) {
        JpaSentNotificationEntity entity = repository.findById(commandId)
                .orElseThrow(() -> new IllegalStateException("Notification not reserved commandId=" + commandId));
        entity.setRecipient(recipient);
        entity.setStatus(JpaSentNotificationEntity.Status.SENT.name());
        entity.setSentAt(Instant.now());
        entity.setError(null);
        repository.save(entity);
    }

    @Override
    public void markFailed(String commandId, String recipient, String error) {
        JpaSentNotificationEntity entity = repository.findById(commandId)
                .orElseThrow(() -> new IllegalStateException("Notification not reserved commandId=" + commandId));
        entity.setRecipient(recipient);
        entity.setStatus(JpaSentNotificationEntity.Status.FAILED.name());
        entity.setSentAt(Instant.now());
        entity.setError(error);
        entity.setAttempts(entity.getAttempts() + 1);
        repository.save(entity);
    }

    @Override
    public boolean markRetrying(String commandId) {
        JpaSentNotificationEntity entity = repository.findById(commandId).orElse(null);
        if (entity == null) {
            return false;
        }
        if (JpaSentNotificationEntity.Status.RETRYING.name().equals(entity.getStatus())) {
            return true;
        }
        if (!JpaSentNotificationEntity.Status.FAILED.name().equals(entity.getStatus())) {
            return false;
        }
        entity.setStatus(JpaSentNotificationEntity.Status.RETRYING.name());
        repository.save(entity);
        return true;
    }

    @Override
    public void markIgnored(String commandId, String reason) {
        JpaSentNotificationEntity entity = repository.findById(commandId)
                .orElseThrow(() -> new IllegalStateException("Notification not reserved commandId=" + commandId));
        entity.setStatus(JpaSentNotificationEntity.Status.IGNORED.name());
        entity.setSentAt(Instant.now());
        entity.setError(reason);
        repository.save(entity);
    }

    @Override
    public List<FailedNotification> findFailedForRetry(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<String> statuses = List.of(
                JpaSentNotificationEntity.Status.FAILED.name(),
                JpaSentNotificationEntity.Status.RETRYING.name()
        );
        return repository.findByStatusInOrderBySentAtAsc(statuses, PageRequest.of(0, limit))
                .stream()
                .map(entity -> new FailedNotification(
                        entity.getCommandId(),
                        entity.getTemplate(),
                        entity.getOrderId(),
                        entity.getCustomerId(),
                        entity.getRecipient(),
                        entity.getSubject(),
                        entity.getBody(),
                        entity.getBodyHtml(),
                        entity.getAttempts()
                ))
                .toList();
    }
}
