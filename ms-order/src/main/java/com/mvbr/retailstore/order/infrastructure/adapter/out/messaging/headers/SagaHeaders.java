package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers;

import org.slf4j.MDC;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SagaHeaders {

    // Consistência com x-topic-version = v1
    private static final String SCHEMA_VERSION = "v1";

    // Identidade do produtor
    private static final String PRODUCER = "ms-order";

    // Default do canal/tópico
    private static final String DEFAULT_TOPIC_VERSION = "v1";

    // Payload encoding
    private static final String CONTENT_TYPE = "application/json";

    private SagaHeaders() {
    }

    /**
     * Mantém compatibilidade com o uso antigo.
     * (Sem aggregate headers explícitos)
     */
    public static Map<String, String> build(String eventId, String eventType, String occurredAt) {
        return build(eventId, eventType, occurredAt, null, null, DEFAULT_TOPIC_VERSION);
    }

    /**
     * Versão recomendada:
     * - inclui aggregateType e aggregateId
     * - inclui topicVersion (ex: v1)
     * - correlationId/causationId/traceparent via MDC quando existir
     * - fallback robusto para correlationId e causationId
     */
    public static Map<String, String> build(String eventId,
                                            String eventType,
                                            String occurredAt,
                                            String aggregateType,
                                            String aggregateId,
                                            String topicVersion) {

        // ============================
        // Hard requirements (não deixa header “podre” sair)
        // ============================
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (occurredAt == null || occurredAt.isBlank()) {
            throw new IllegalArgumentException("occurredAt is required");
        }

        Map<String, String> headers = new LinkedHashMap<>();

        // ============================
        // Event envelope
        // ============================
        headers.put(HeaderNames.EVENT_ID, eventId);
        headers.put(HeaderNames.EVENT_TYPE, eventType);
        headers.put(HeaderNames.OCCURRED_AT, occurredAt);

        headers.put(HeaderNames.SCHEMA_VERSION, SCHEMA_VERSION);
        headers.put(
                HeaderNames.TOPIC_VERSION,
                (topicVersion == null || topicVersion.isBlank()) ? DEFAULT_TOPIC_VERSION : topicVersion
        );
        headers.put(HeaderNames.PRODUCER, PRODUCER);

        // ============================
        // Aggregate info (útil pra consumers e debugging)
        // ============================
        if (aggregateType != null && !aggregateType.isBlank()) {
            headers.put(HeaderNames.AGGREGATE_TYPE, aggregateType);
        }
        if (aggregateId != null && !aggregateId.isBlank()) {
            headers.put(HeaderNames.AGGREGATE_ID, aggregateId);
        }

        // ============================
        // Correlation / causation
        // ============================
        String correlationId = resolveFromMdc(HeaderNames.CORRELATION_ID)
                .orElseGet(() -> UUID.randomUUID().toString());
        headers.put(HeaderNames.CORRELATION_ID, correlationId);

        // Se não vier do MDC, usa o próprio eventId (cadeia mínima útil)
        String causationId = resolveFromMdc(HeaderNames.CAUSATION_ID).orElse(eventId);
        headers.put(HeaderNames.CAUSATION_ID, causationId);

        // ============================
        // Trace propagation (W3C)
        // ============================
        resolveFromMdc(HeaderNames.TRACEPARENT)
                .ifPresent(tp -> headers.put(HeaderNames.TRACEPARENT, tp));

        // ============================
        // Content type
        // ============================
        headers.put(HeaderNames.CONTENT_TYPE, CONTENT_TYPE);

        // ============================
        // (Opcional) Saga fields - se o orchestrator colocar no MDC, a gente propaga
        // ============================
        resolveFromMdc(HeaderNames.SAGA_ID).ifPresent(v -> headers.put(HeaderNames.SAGA_ID, v));
        resolveFromMdc(HeaderNames.SAGA_NAME).ifPresent(v -> headers.put(HeaderNames.SAGA_NAME, v));
        resolveFromMdc(HeaderNames.SAGA_STEP).ifPresent(v -> headers.put(HeaderNames.SAGA_STEP, v));

        return headers;
    }

    private static Optional<String> resolveFromMdc(String key) {
        String value = MDC.get(key);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }
}
