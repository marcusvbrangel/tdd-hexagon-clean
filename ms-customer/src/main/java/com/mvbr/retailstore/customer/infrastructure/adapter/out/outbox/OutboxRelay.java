package com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.entity.OutboxMessageJpaEntity;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.repository.OutboxJpaRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
@ConditionalOnProperty(prefix = "outbox.relay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = Logger.getLogger(OutboxRelay.class.getName());

    private final OutboxJpaRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final long sendTimeoutMs;

    public OutboxRelay(OutboxJpaRepository outboxRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       ObjectMapper objectMapper,
                       @Value("${outbox.relay.sendTimeoutMs:10000}") long sendTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sendTimeoutMs = sendTimeoutMs;
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

                kafkaTemplate.send(record).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
                msg.markPublished();
                log.info("Outbox publish success id=" + msg.getId() + " topic=" + msg.getTopic());
            } catch (TimeoutException e) {
                String error = formatError(e);
                log.warning("Outbox publish timeout id=" + msg.getId()
                        + " topic=" + msg.getTopic()
                        + " error=" + error);
                msg.markFailed(error);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                String error = formatError(e);
                log.log(Level.WARNING, "Outbox publish interrupted id=" + msg.getId()
                        + " topic=" + msg.getTopic()
                        + " error=" + error, e);
                msg.markFailed(error);
                break;
            } catch (ExecutionException | RuntimeException e) {
                String error = formatError(e);
                log.log(Level.WARNING, "Outbox publish failed id=" + msg.getId()
                        + " topic=" + msg.getTopic()
                        + " error=" + error, e);
                msg.markFailed(error);
            }
        }
    }

    private Map<String, String> parseHeaders(OutboxMessageJpaEntity msg) {
        try {
            return objectMapper.readValue(msg.getHeadersJson(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not parse headers for outbox id=" + msg.getId(), e);
        }
    }

    private String formatError(Throwable throwable) {
        Throwable root = rootCause(throwable);
        String message = root.getMessage();
        String result = root.getClass().getSimpleName();
        if (message != null && !message.isBlank()) {
            result = result + ": " + message;
        }
        return truncate(result, 512);
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
