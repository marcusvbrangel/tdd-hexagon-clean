package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SagaHeadersTest {

    @Test
    void forCommand_sets_required_headers_and_defaults() {
        Map<String, String> headers = SagaHeaders.forCommand(
                null,
                "saga-1",
                "",
                "",
                "checkout",
                "STEP",
                "Order",
                "order-1"
        );

        assertThat(headers.get(HeaderNames.EVENT_ID)).isNotBlank();
        assertThat(headers.get(HeaderNames.OCCURRED_AT)).isNotBlank();
        assertThat(headers.get(HeaderNames.PRODUCER)).isEqualTo("ms-checkout-orchestrator");
        assertThat(headers.get(HeaderNames.SCHEMA_VERSION)).isEqualTo("v1");
        assertThat(headers.get(HeaderNames.TOPIC_VERSION)).isEqualTo("v1");
        assertThat(headers.get(HeaderNames.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(headers.get(HeaderNames.CORRELATION_ID)).isEqualTo("order-1");
        assertThat(headers.get(HeaderNames.CAUSATION_ID)).isEqualTo("order-1");
        assertThat(headers.get(HeaderNames.SAGA_ID)).isEqualTo("saga-1");
        assertThat(headers.get(HeaderNames.SAGA_NAME)).isEqualTo("checkout");
        assertThat(headers.get(HeaderNames.SAGA_STEP)).isEqualTo("STEP");
        assertThat(headers.get(HeaderNames.AGGREGATE_TYPE)).isEqualTo("Order");
        assertThat(headers.get(HeaderNames.AGGREGATE_ID)).isEqualTo("order-1");
    }

    @Test
    void forCommand_respects_given_event_id_and_causation() {
        Map<String, String> headers = SagaHeaders.forCommand(
                "cmd-1",
                "saga-1",
                "corr-1",
                "cause-1",
                "checkout",
                "STEP",
                "Order",
                "order-1"
        );

        assertThat(headers.get(HeaderNames.EVENT_ID)).isEqualTo("cmd-1");
        assertThat(headers.get(HeaderNames.CORRELATION_ID)).isEqualTo("corr-1");
        assertThat(headers.get(HeaderNames.CAUSATION_ID)).isEqualTo("cause-1");
    }
}
