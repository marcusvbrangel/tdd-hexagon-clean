package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.headers;

import com.mvbr.retailstore.payment.application.command.SagaContext;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Builder de headers para eventos de payment.
 */
public final class SagaHeaders {

    private static final String PRODUCER = "ms-payment";
    private static final String SCHEMA_VERSION = "v1";
    private static final String TOPIC_VERSION = "v1";
    private static final String CONTENT_TYPE = "application/json";

    private SagaHeaders() {
    }

    public static Map<String, String> forEvent(String eventId,
                                               String eventType,
                                               String occurredAt,
                                               String aggregateType,
                                               String aggregateId,
                                               SagaContext ctx) {
        Map<String, String> headers = new LinkedHashMap<>();

        String resolvedEventId = (eventId == null || eventId.isBlank())
                ? UUID.randomUUID().toString()
                : eventId;
        String resolvedOccurredAt = (occurredAt == null || occurredAt.isBlank())
                ? Instant.now().toString()
                : occurredAt;

        String resolvedAggregateType = (aggregateType == null || aggregateType.isBlank())
                ? (ctx != null ? ctx.aggregateType() : null)
                : aggregateType;
        String resolvedAggregateId = (aggregateId == null || aggregateId.isBlank())
                ? (ctx != null ? ctx.aggregateId() : null)
                : aggregateId;

        headers.put(HeaderNames.EVENT_ID, resolvedEventId);
        headers.put(HeaderNames.EVENT_TYPE, eventType);
        headers.put(HeaderNames.OCCURRED_AT, resolvedOccurredAt);

        headers.put(HeaderNames.PRODUCER, PRODUCER);
        headers.put(HeaderNames.SCHEMA_VERSION, SCHEMA_VERSION);
        headers.put(HeaderNames.TOPIC_VERSION, TOPIC_VERSION);
        headers.put(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);

        headers.put(HeaderNames.COMMAND_ID, resolvedEventId);
        headers.put(HeaderNames.COMMAND_TYPE, eventType);

        if (resolvedAggregateType != null && !resolvedAggregateType.isBlank()) {
            headers.put(HeaderNames.AGGREGATE_TYPE, resolvedAggregateType);
        }
        if (resolvedAggregateId != null && !resolvedAggregateId.isBlank()) {
            headers.put(HeaderNames.AGGREGATE_ID, resolvedAggregateId);
        }

        String correlationId = ctx != null ? ctx.correlationId() : null;
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = resolvedAggregateId;
        }
        if (correlationId != null && !correlationId.isBlank()) {
            headers.put(HeaderNames.CORRELATION_ID, correlationId);
        }

        String causationId = ctx != null ? ctx.causationId() : null;
        if (causationId == null || causationId.isBlank()) {
            causationId = resolvedEventId;
        }
        if (causationId != null && !causationId.isBlank()) {
            headers.put(HeaderNames.CAUSATION_ID, causationId);
        }

        if (ctx != null) {
            putIfNotBlank(headers, HeaderNames.SAGA_ID, ctx.sagaId());
            putIfNotBlank(headers, HeaderNames.SAGA_NAME, ctx.sagaName());
            putIfNotBlank(headers, HeaderNames.SAGA_STEP, ctx.sagaStep());
        }

        return headers;
    }

    private static void putIfNotBlank(Map<String, String> headers, String key, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(key, value);
        }
    }
}
