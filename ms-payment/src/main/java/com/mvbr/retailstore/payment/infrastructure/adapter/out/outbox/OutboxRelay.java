package com.mvbr.retailstore.payment.infrastructure.adapter.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

/**
 * Job que publica mensagens da outbox no Kafka.
 */
@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = Logger.getLogger(OutboxRelay.class.getName());

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxJpaRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
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
                        Instant.now()
                );

        if (!pending.isEmpty()) {
            log.info("OutboxRelay tick - pending size: " + pending.size());
        }

        for (OutboxMessageJpaEntity msg : pending) {
            try {
                msg.markInProgress();

                ProducerRecord<String, String> record = new ProducerRecord<>(
                        msg.getTopic(),
                        msg.getAggregateId(),
                        msg.getPayloadJson()
                );
                parseHeaders(msg).forEach((name, value) ->
                        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));

                kafkaTemplate.send(record).get();
                msg.markPublished();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                msg.markFailed(e.getMessage());
                throw new IllegalStateException("Thread interrupted while publishing outbox id=" + msg.getId(), e);
            } catch (ExecutionException | RuntimeException e) {
                log.warning("Outbox publish failed msg.getId(): " + msg.getId() + ", e.getMessage(): " + e.getMessage());
                msg.markFailed(e.getMessage());
            }
        }
    }

    private Map<String, String> parseHeaders(OutboxMessageJpaEntity msg) {
        try {
            return objectMapper.readValue(msg.getHeadersJson(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not parse headers for outbox id=" + msg.getId(), e);
        }
    }
}
