package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.headers;

import com.mvbr.retailstore.payment.application.command.SagaContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SagaHeadersTest {

    @Test
    void forEvent_sets_required_headers_and_defaults() {
        Map<String, String> headers = SagaHeaders.forEvent(
                null,
                "payment.authorized",
                "",
                "Order",
                "order-1",
                null
        );

        assertThat(headers.get(HeaderNames.EVENT_ID)).isNotBlank();
        assertThat(headers.get(HeaderNames.COMMAND_ID)).isNotBlank();
        assertThat(headers.get(HeaderNames.EVENT_TYPE)).isEqualTo("payment.authorized");
        assertThat(headers.get(HeaderNames.PRODUCER)).isEqualTo("ms-payment");
        assertThat(headers.get(HeaderNames.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(headers.get(HeaderNames.CORRELATION_ID)).isEqualTo("order-1");
        assertThat(headers.get(HeaderNames.AGGREGATE_ID)).isEqualTo("order-1");
    }

    @Test
    void forEvent_respects_context_headers() {
        SagaContext ctx = new SagaContext(
                "saga-1",
                "corr-1",
                "cause-1",
                "checkout",
                "WAIT_PAYMENT",
                "Order",
                "order-2"
        );

        Map<String, String> headers = SagaHeaders.forEvent(
                "evt-1",
                "payment.declined",
                "2025-01-01T00:00:00Z",
                "Order",
                "order-2",
                ctx
        );

        assertThat(headers.get(HeaderNames.EVENT_ID)).isEqualTo("evt-1");
        assertThat(headers.get(HeaderNames.CORRELATION_ID)).isEqualTo("corr-1");
        assertThat(headers.get(HeaderNames.CAUSATION_ID)).isEqualTo("cause-1");
        assertThat(headers.get(HeaderNames.SAGA_ID)).isEqualTo("saga-1");
        assertThat(headers.get(HeaderNames.SAGA_STEP)).isEqualTo("WAIT_PAYMENT");
    }
}
