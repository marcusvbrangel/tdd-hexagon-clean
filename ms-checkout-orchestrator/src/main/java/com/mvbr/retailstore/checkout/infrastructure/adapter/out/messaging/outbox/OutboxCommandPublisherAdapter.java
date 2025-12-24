package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.application.port.out.CommandPublisher;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Primary
@Component
/**
 * Adapter de saida que grava comandos na tabela outbox.
 * Chamado pelo CheckoutSagaCommandSender via CommandPublisher.
 */
public class OutboxCommandPublisherAdapter implements CommandPublisher {

    private static final String AGGREGATE_TYPE = "CheckoutSaga";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxCommandPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Persiste um comando na outbox com headers serializados.
     * O OutboxRelay ira publicar depois no Kafka.
     */
    @Override
    public void publish(String topic, String key, String commandType, Object payload, Map<String, String> headers) {
        String payloadJson = write(payload);

        Map<String, String> merged = new LinkedHashMap<>(headers);
        merged.put(HeaderNames.EVENT_TYPE, commandType);
        merged.put(HeaderNames.COMMAND_TYPE, commandType);

        String eventId = merged.get(HeaderNames.EVENT_ID);
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("headers must contain x-event-id");
        }
        merged.putIfAbsent(HeaderNames.COMMAND_ID, eventId);

        String headersJson = write(merged);

        OutboxMessageJpaEntity msg = new OutboxMessageJpaEntity(
                eventId,
                AGGREGATE_TYPE,
                key,
                commandType,
                topic,
                payloadJson,
                headersJson,
                Instant.now()
        );

        outboxRepository.save(msg);
    }

    /**
     * Serializa objetos para JSON para persistir na outbox.
     */
    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize value to JSON", e);
        }
    }
}
