package com.mvbr.retailstore.checkout.infrastructure.adapter.in.messaging.envelope;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    @Test
    void from_consumer_record_reads_headers_and_payload() throws Exception {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("order.events.v1", 0, 0L, "order-1", "{\"value\":1}");
        record.headers().add(HeaderNames.EVENT_ID, "evt-1".getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.EVENT_TYPE, "order.placed".getBytes(StandardCharsets.UTF_8));
        record.headers().add(HeaderNames.AGGREGATE_ID, "agg-1".getBytes(StandardCharsets.UTF_8));

        EventEnvelope env = EventEnvelope.from(record);

        assertThat(env.eventId()).isEqualTo("evt-1");
        assertThat(env.eventType()).isEqualTo("order.placed");
        assertThat(env.aggregateIdOrKey()).isEqualTo("agg-1");
        assertThat(env.correlationIdOr("fallback")).isEqualTo("fallback");

        ObjectMapper mapper = new ObjectMapper();
        Map<?, ?> payload = env.readPayload(mapper, Map.class);
        assertThat(payload.get("value")).isEqualTo(1);
    }
}
