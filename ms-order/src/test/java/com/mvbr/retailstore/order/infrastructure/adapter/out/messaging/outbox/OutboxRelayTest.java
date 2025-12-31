package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers.HeaderNames;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    @Mock
    private OutboxJpaRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should publish pending outbox message using stored topic, key, headers and payload")
    void publishesOutboxMessage() throws Exception {
        String occurredAt = "2024-01-02T12:00:00Z";
        OutboxMessageJpaEntity msg = new OutboxMessageJpaEntity(
                "evt-123",
                "Order",
                "ord-123",
                "OrderPlaced",
                "order.placed",
                "{\"eventId\":\"evt-123\"}",
                objectMapper.writeValueAsString(SagaHeaders.build("evt-123", "OrderPlaced", occurredAt)),
                Instant.parse(occurredAt)
        );

        when(outboxRepository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(anyList(), any(Instant.class)))
                .thenReturn(List.of(msg));

        ArgumentCaptor<ProducerRecord<String, String>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(new SendResult<>(null, null));
        when(kafkaTemplate.send(captor.capture())).thenReturn(future);

        OutboxRelay relay = new OutboxRelay(outboxRepository, kafkaTemplate, objectMapper);
        relay.tick();

        ProducerRecord<String, String> record = captor.getValue();
        assertThat(record.topic()).isEqualTo("order.placed");
        assertThat(record.key()).isEqualTo("ord-123");
        assertThat(record.value()).isEqualTo("{\"eventId\":\"evt-123\"}");
        assertThat(new String(record.headers().lastHeader(HeaderNames.EVENT_ID).value(), StandardCharsets.UTF_8))
                .isEqualTo("evt-123");
        assertThat(new String(record.headers().lastHeader(HeaderNames.EVENT_TYPE).value(), StandardCharsets.UTF_8))
                .isEqualTo("OrderPlaced");
        assertThat(msg.getStatus()).isEqualTo(OutboxMessageJpaEntity.Status.PUBLISHED.name());
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }
}
