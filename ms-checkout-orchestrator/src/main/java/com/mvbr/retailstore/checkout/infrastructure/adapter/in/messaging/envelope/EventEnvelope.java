package com.mvbr.retailstore.checkout.infrastructure.adapter.in.messaging.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

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

    private static Optional<String> header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            return Optional.empty();
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }

    public String aggregateIdOrKey() {
        return (aggregateId != null && !aggregateId.isBlank()) ? aggregateId : key;
    }

    public String correlationIdOr(String fallback) {
        return (correlationId != null && !correlationId.isBlank()) ? correlationId : fallback;
    }

    public <T> T readPayload(ObjectMapper mapper, Class<T> clazz) {
        try {
            return mapper.readValue(payloadJson, clazz);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse payload as " + clazz.getSimpleName()
                    + " topic=" + topic + " key=" + key, e);
        }
    }
}
