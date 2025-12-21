package com.mvbr.retailstore.customer.infrastructure.adapter.out.saga;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SagaHeadersTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void buildDefaultsToEventIdWhenMdcIsEmpty() {
        Map<String, String> headers = SagaHeaders.build(
                "event-1",
                "customer.created",
                "2025-01-01T10:00:00Z"
        );

        assertEquals("event-1", headers.get(HeaderNames.EVENT_ID));
        assertEquals("customer.created", headers.get(HeaderNames.EVENT_TYPE));
        assertEquals("1", headers.get(HeaderNames.SCHEMA_VERSION));
        assertEquals("customer-service", headers.get(HeaderNames.PRODUCER));
        assertEquals("2025-01-01T10:00:00Z", headers.get(HeaderNames.OCCURRED_AT));
        assertEquals("event-1", headers.get(HeaderNames.CORRELATION_ID));
        assertEquals("event-1", headers.get(HeaderNames.CAUSATION_ID));
        assertEquals("application/json", headers.get(HeaderNames.CONTENT_TYPE));
        assertFalse(headers.containsKey(HeaderNames.TRACEPARENT));
    }

    @Test
    void buildUsesMdcValuesWhenProvided() {
        MDC.put(HeaderNames.CORRELATION_ID, "corr-1");
        MDC.put(HeaderNames.CAUSATION_ID, "cause-1");
        MDC.put(HeaderNames.TRACEPARENT, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        Map<String, String> headers = SagaHeaders.build(
                "event-2",
                "customer.changed",
                "2025-01-02T10:00:00Z"
        );

        assertEquals("corr-1", headers.get(HeaderNames.CORRELATION_ID));
        assertEquals("cause-1", headers.get(HeaderNames.CAUSATION_ID));
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                headers.get(HeaderNames.TRACEPARENT));
        assertTrue(headers.containsKey(HeaderNames.TRACEPARENT));
    }

    @Test
    void buildFallsBackWhenCorrelationIdIsBlank() {
        MDC.put(HeaderNames.CORRELATION_ID, " ");

        Map<String, String> headers = SagaHeaders.build(
                "event-3",
                "customer.changed",
                "2025-01-03T10:00:00Z"
        );

        assertEquals("event-3", headers.get(HeaderNames.CORRELATION_ID));
        assertEquals("event-3", headers.get(HeaderNames.CAUSATION_ID));
    }
}
