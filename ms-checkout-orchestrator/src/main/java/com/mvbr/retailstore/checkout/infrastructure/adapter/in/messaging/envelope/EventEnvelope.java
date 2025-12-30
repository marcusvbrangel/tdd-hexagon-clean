package com.mvbr.retailstore.checkout.infrastructure.adapter.in.messaging.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Envelope padrao para eventos Kafka com payload e headers tipados.
 * Criado pelo CheckoutEventsConsumer ao receber mensagens.
 */
public record EventEnvelope(
        String topic,
        String key,
        String payloadJson,
        String eventId,
        String eventType,
        String occurredAt,
        String correlationId,
        String causationId,
        String sagaId,
        String sagaName,
        String sagaStep,
        String aggregateType,
        String aggregateId
) {

    /**
     * Cria o envelope a partir de um ConsumerRecord do Kafka.
     */
    public static EventEnvelope from(ConsumerRecord<String, String> record) {
        return new EventEnvelope(
                record.topic(),
                record.key(),
                record.value(),
                header(record, HeaderNames.EVENT_ID).orElse(""),
                header(record, HeaderNames.EVENT_TYPE).orElse(""),
                header(record, HeaderNames.OCCURRED_AT).orElse(""),
                header(record, HeaderNames.CORRELATION_ID).orElse(""),
                header(record, HeaderNames.CAUSATION_ID).orElse(""),
                header(record, HeaderNames.SAGA_ID).orElse(""),
                header(record, HeaderNames.SAGA_NAME).orElse(""),
                header(record, HeaderNames.SAGA_STEP).orElse(""),
                header(record, HeaderNames.AGGREGATE_TYPE).orElse(""),
                header(record, HeaderNames.AGGREGATE_ID).orElse(record.key())
        );
    }

    /**
     * Le um headers do Kafka convertendo para string.
     */
    private static Optional<String> header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            return Optional.empty();
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }

    /**
     * Retorna o aggregateId, usando a key do Kafka como fallback.
     */
    public String aggregateIdOrKey() {
        return (aggregateId != null && !aggregateId.isBlank()) ? aggregateId : key;
    }

    /**
     * Retorna o correlationId, usando o fallback quando nao informado.
     */
    public String correlationIdOr(String fallback) {
        return (correlationId != null && !correlationId.isBlank()) ? correlationId : fallback;
    }

    /**
     * Desserializa o payload JSON usando Jackson.
     */
    public <T> T readPayload(ObjectMapper mapper, Class<T> clazz) {
        try {
            return mapper.readValue(payloadJson, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse payload as " + clazz.getSimpleName()
                    + " topic=" + topic + " key=" + key, e);
        }
    }
}
