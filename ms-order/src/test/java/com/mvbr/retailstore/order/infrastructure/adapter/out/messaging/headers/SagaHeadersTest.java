package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SagaHeadersTest {

    @Test
    @DisplayName("Should build required headers with defaults when MDC is empty")
    void buildHeadersWithDefaults() {
        Map<String, String> headers = SagaHeaders.build("evt-1", "OrderPlaced", "2024-01-02T10:00:00Z");

        assertThat(headers)
                .containsEntry(HeaderNames.EVENT_ID, "evt-1")
                .containsEntry(HeaderNames.EVENT_TYPE, "OrderPlaced")
                .containsEntry(HeaderNames.SCHEMA_VERSION, "1")
                .containsEntry(HeaderNames.PRODUCER, "order-service")
                .containsEntry(HeaderNames.OCCURRED_AT, "2024-01-02T10:00:00Z")
                .containsEntry(HeaderNames.CORRELATION_ID, "evt-1")
                .containsEntry(HeaderNames.CAUSATION_ID, "evt-1")
                .containsEntry(HeaderNames.CONTENT_TYPE, "application/json");
        assertThat(headers).doesNotContainKey(HeaderNames.TRACEPARENT);
    }

    @Test
    @DisplayName("Should honor MDC values for correlation, causation and traceparent")
    void buildHeadersUsingMdc() {
        try {
            MDC.put(HeaderNames.CORRELATION_ID, "corr-123");
            MDC.put(HeaderNames.CAUSATION_ID, "cause-456");
            MDC.put(HeaderNames.TRACEPARENT, "00-abc-def-01");

            Map<String, String> headers = SagaHeaders.build("evt-2", "OrderPlaced", "2024-01-02T11:00:00Z");

            assertThat(headers.get(HeaderNames.CORRELATION_ID)).isEqualTo("corr-123");
            assertThat(headers.get(HeaderNames.CAUSATION_ID)).isEqualTo("cause-456");
            assertThat(headers.get(HeaderNames.TRACEPARENT)).isEqualTo("00-abc-def-01");
        } finally {
            MDC.clear();
        }
    }
}
