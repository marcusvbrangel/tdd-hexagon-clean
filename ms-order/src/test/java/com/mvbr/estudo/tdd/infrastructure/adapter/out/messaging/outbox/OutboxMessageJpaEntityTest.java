package com.mvbr.estudo.tdd.infrastructure.adapter.out.messaging.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxMessageJpaEntityTest {

    @Test
    @DisplayName("markFailed should increase retry count and push next attempt to the future")
    void markFailedBackoff() {
        OutboxMessageJpaEntity msg = new OutboxMessageJpaEntity(
                "evt-1",
                "Order",
                "ord-1",
                "OrderPlacedEvent",
                "{}",
                Instant.now()
        );

        Instant firstAttempt = msg.getNextAttemptAt();
        msg.markFailed("kafka down");

        assertThat(msg.getStatus()).isEqualTo(OutboxMessageJpaEntity.Status.FAILED.name());
        assertThat(msg.getRetryCount()).isEqualTo(1);
        assertThat(msg.getNextAttemptAt()).isAfter(firstAttempt);
        assertThat(msg.getLastError()).contains("kafka down");
    }
}
