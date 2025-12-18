package com.mvbr.estudo.tdd.infrastructure.adapter.out.messaging.outbox;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;import java.util.logging.Logger;

@Component
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
                outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxMessageJpaEntity.Status.PENDING.name());

        log.info("OutboxRelay tick - pending size: " + pending.size());

        for (OutboxMessageJpaEntity msg : pending) {
            try {

                log.info("Message Pending ::: Event Type: " + msg.getEventType() + ", Aggregate ID: " + msg.getAggregateId() + ", Payload: " + msg.getPayloadJson());

                kafkaTemplate.send(msg.getEventType(), msg.getAggregateId(), msg.getPayloadJson());
                msg.markPublished();
            } catch (Exception e) {
                log.warning("Outbox publish failed msg.getId(): " + msg.getId() + ", e.getMessage(): " + e.getMessage());
                msg.markFailed();
            }
        }
    }
}
