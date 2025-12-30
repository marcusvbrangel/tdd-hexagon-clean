package com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.inventory.config.InventoryOutboxProperties;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxJpaRepository;
import com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence.OutboxMessageJpaEntity;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

/**
 * Job que publica mensagens da outbox no Kafka.
 * Fluxo: tabela outbox -> OutboxRelay -> Kafka.
 */
@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = Logger.getLogger(OutboxRelay.class.getName());

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final InventoryOutboxProperties props;

    public OutboxRelay(OutboxJpaRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       ObjectMapper objectMapper,
                       InventoryOutboxProperties props) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    /**
     * Varre mensagens pendentes e tenta publicar uma a uma.
     *
     * - fixedDelayMs vem de outbox.relay.fixedDelayMs
     * - batchSize vem de outbox.relay.batchSize
     */
    @Scheduled(fixedDelayString = "${outbox.relay.fixedDelayMs:10000}")
    @Transactional
    public void tick() {
        int batchSize = props.getRelay().getBatchSize();
        if (batchSize <= 0) batchSize = 100;

        List<OutboxMessageJpaEntity> pending =
                outboxRepository.findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(
                                OutboxMessageJpaEntity.Status.PENDING.name(),
                                OutboxMessageJpaEntity.Status.FAILED.name()
                        ),
                        Instant.now(),
                        PageRequest.of(0, batchSize)
                );

        if (pending.isEmpty()) return;

        log.info("OutboxRelay tick - pending size: " + pending.size());

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

                // Publish sync (MVP ok). Se quiser, depois a gente torna async com callback.
                kafkaTemplate.send(record).get();

                msg.markPublished();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                msg.markFailed(e.getMessage());
                throw new IllegalStateException("Thread interrupted while publishing outbox id=" + msg.getId(), e);

            } catch (ExecutionException | RuntimeException e) {
                log.warning("Outbox publish failed id=" + msg.getId() + " err=" + e.getMessage());
                msg.markFailed(e.getMessage());
            }
        }
    }

    /**
     * Converte JSON de headers persistidos em mapa.
     */
    private Map<String, String> parseHeaders(OutboxMessageJpaEntity msg) {
        String json = msg.getHeadersJson();
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not parse headers for outbox id=" + msg.getId(), e);
        }
    }
}
