package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class SagaHeaders {

    private static final String PRODUCER = "ms-checkout-orchestrator";
    private static final String SCHEMA_VERSION = "v1";
    private static final String TOPIC_VERSION = "v1";
    private static final String CONTENT_TYPE = "application/json";

    private SagaHeaders() {}

    public static Map<String, String> forCommand(
            String eventId,
            String sagaId,
            String correlationId,
            String causationId,
            String sagaName,
            String sagaStep,
            String aggregateType,
            String aggregateId
    ) {
        Map<String, String> headers = new LinkedHashMap<>();

        String resolvedEventId = (eventId == null || eventId.isBlank())
                ? UUID.randomUUID().toString()
                : eventId;

        headers.put(HeaderNames.EVENT_ID, resolvedEventId);
        headers.put(HeaderNames.OCCURRED_AT, Instant.now().toString());

        headers.put(HeaderNames.PRODUCER, PRODUCER);
        headers.put(HeaderNames.SCHEMA_VERSION, SCHEMA_VERSION);
        headers.put(HeaderNames.TOPIC_VERSION, TOPIC_VERSION);
        headers.put(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);

        headers.put(
                HeaderNames.CORRELATION_ID,
                (correlationId == null || correlationId.isBlank()) ? aggregateId : correlationId
        );
        headers.put(
                HeaderNames.CAUSATION_ID,
                (causationId == null || causationId.isBlank()) ? aggregateId : causationId
        );

        if (aggregateType != null && !aggregateType.isBlank()) {
            headers.put(HeaderNames.AGGREGATE_TYPE, aggregateType);
        }
        if (aggregateId != null && !aggregateId.isBlank()) {
            headers.put(HeaderNames.AGGREGATE_ID, aggregateId);
        }

        if (sagaId != null && !sagaId.isBlank()) {
            headers.put(HeaderNames.SAGA_ID, sagaId);
        }
        if (sagaName != null && !sagaName.isBlank()) {
            headers.put(HeaderNames.SAGA_NAME, sagaName);
        }
        if (sagaStep != null && !sagaStep.isBlank()) {
            headers.put(HeaderNames.SAGA_STEP, sagaStep);
        }

        return headers;
    }
}
