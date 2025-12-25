package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.inventory.application.port.out.EventPublisher;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.OutboxJpaRepository;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.OutboxMessageJpaEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class OutboxEventPublisherAdapter implements EventPublisher {

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void publish(String topic,
                        String aggregateType,
                        String aggregateId,
                        String eventType,
                        Object payload,
                        Map<String, String> headers,
                        Instant occurredAt) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String headersJson = objectMapper.writeValueAsString(headers);

            String eventId = extractEventId(payload).orElse(UUID.randomUUID().toString());

            OutboxMessageJpaEntity msg = new OutboxMessageJpaEntity(
                    eventId,
                    aggregateType,
                    aggregateId,
                    eventType,
                    topic,
                    payloadJson,
                    headersJson,
                    occurredAt != null ? occurredAt : Instant.now()
            );

            outboxRepository.save(msg);
        } catch (Exception e) {
            throw new RuntimeException("Failed to write outbox message for eventType=" + eventType, e);
        }
    }

    private Optional<String> extractEventId(Object payload) {
        try {
            var m = payload.getClass().getMethod("eventId");
            Object v = m.invoke(payload);
            if (v != null) {
                return Optional.of(v.toString());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }
}
