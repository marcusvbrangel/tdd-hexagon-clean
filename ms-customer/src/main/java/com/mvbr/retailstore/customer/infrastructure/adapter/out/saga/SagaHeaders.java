package com.mvbr.retailstore.customer.infrastructure.adapter.out.saga;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class SagaHeaders {

    private static final String SCHEMA_VERSION = "1";
    private static final String PRODUCER = "customer-service";
    private static final String CONTENT_TYPE = "application/json";

    private SagaHeaders() {
    }

    public static Map<String, String> build(String eventId, String eventType, String occurredAt) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HeaderNames.EVENT_ID, eventId);
        headers.put(HeaderNames.EVENT_TYPE, eventType);
        headers.put(HeaderNames.SCHEMA_VERSION, SCHEMA_VERSION);
        headers.put(HeaderNames.PRODUCER, PRODUCER);
        headers.put(HeaderNames.OCCURRED_AT, occurredAt);

        String correlationId = resolveFromMdc(HeaderNames.CORRELATION_ID).orElse(eventId);
        headers.put(HeaderNames.CORRELATION_ID, correlationId);
        headers.put(HeaderNames.CAUSATION_ID, resolveFromMdc(HeaderNames.CAUSATION_ID).orElse(correlationId));

        resolveFromMdc(HeaderNames.TRACEPARENT)
                .ifPresent(traceparent -> headers.put(HeaderNames.TRACEPARENT, traceparent));
        headers.put(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);
        return headers;
    }

    private static Optional<String> resolveFromMdc(String key) {
        String value = MDC.get(key);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }
}
