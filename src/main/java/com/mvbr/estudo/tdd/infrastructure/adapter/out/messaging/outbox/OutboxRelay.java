package com.mvbr.estudo.tdd.infrastructure.adapter.out.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = Logger.getLogger(OutboxRelay.class.getName());

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxJpaRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixedDelayMs:10000}")
    @Transactional
    public void tick() {

        List<OutboxMessageJpaEntity> pending =
                outboxRepository.findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(
                                OutboxMessageJpaEntity.Status.PENDING.name(),
                                OutboxMessageJpaEntity.Status.FAILED.name()
                        ),
                        java.time.Instant.now()
                );

        log.info("OutboxRelay tick - pending size: " + pending.size());

        for (OutboxMessageJpaEntity msg : pending) {
            try {
                msg.markInProgress();
                kafkaTemplate.send(msg.getEventType(), msg.getAggregateId(), msg.getPayloadJson())
                        .thenApply(result -> {
                            result.getProducerRecord().headers()
                                    .add("eventId", msg.getEventId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            return result;
                        })
                        .get();
                msg.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                msg.markFailed(e.getMessage());
                throw new IllegalStateException("Thread interrupted while publishing outbox id=" + msg.getId(), e);
            } catch (ExecutionException e) {
                log.warning("Outbox publish failed msg.getId(): " + msg.getId() + ", e.getMessage(): " + e.getMessage());
                msg.markFailed(e.getMessage());
            }
        }
    }
}
