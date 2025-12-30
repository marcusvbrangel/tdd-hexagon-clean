package com.mvbr.retailstore.inventory.application.service;

import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.config.InventoryInboxProperties;
import com.mvbr.retailstore.inventory.infrastructure.adapter.in.inbox.InboxCommandJpaEntity;
import com.mvbr.retailstore.inventory.infrastructure.adapter.in.inbox.InboxCommandJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class InboxService {

    private final InboxCommandJpaRepository repo;
    private final ClockService clock;
    private final InventoryInboxProperties props;

    public InboxService(InboxCommandJpaRepository repo,
                        ClockService clock,
                        InventoryInboxProperties props) {
        this.repo = repo;
        this.clock = clock;
        this.props = props;
    }

    public enum StartResult {
        STARTED,
        ALREADY_PROCESSED,
        IN_PROGRESS
    }

    @Transactional
    public StartResult tryStart(String commandId,
                                String commandType,
                                String recordKey,
                                String topic,
                                int partition,
                                long offset,
                                String payloadJson,
                                SagaContext sagaContext) {

        validate(commandId, commandType, topic, payloadJson);

        Instant now = clock.now();

        var existingOpt = repo.findById(commandId);
        if (existingOpt.isPresent()) {
            InboxCommandJpaEntity existing = existingOpt.get();

            if (existing.isProcessed()) {
                return StartResult.ALREADY_PROCESSED;
            }

            if (existing.isLocked(now)) {
                return StartResult.IN_PROGRESS;
            }

            // lease expirou -> retoma com tentativa (attempts+1)
            int nextAttempt = safeNextAttempt(existing.getAttempts());
            Duration lease = leaseForAttempt(nextAttempt);

            existing.start(now, lease);
            return StartResult.STARTED;
        }

        // novo comando -> tentativa 1
        Duration lease = leaseForAttempt(1);

        String sagaId = sagaContext == null ? null : sagaContext.sagaId();
        String correlationId = sagaContext == null ? null : sagaContext.correlationId();
        String causationId = sagaContext == null ? null : sagaContext.causationId();
        String sagaName = sagaContext == null ? null : sagaContext.sagaName();
        String sagaStep = sagaContext == null ? null : sagaContext.sagaStep();
        String aggregateType = sagaContext == null ? null : sagaContext.aggregateType();
        String aggregateId = sagaContext == null ? null : sagaContext.aggregateId();

        repo.save(InboxCommandJpaEntity.started(
                commandId,
                commandType,
                recordKey,
                topic,
                partition,
                offset,
                now,
                lease,
                payloadJson,
                sagaId,
                correlationId,
                causationId,
                sagaName,
                sagaStep,
                aggregateType,
                aggregateId
        ));

        return StartResult.STARTED;
    }

    @Transactional
    public void markProcessed(String commandId) {
        Objects.requireNonNull(commandId, "commandId");
        repo.getReferenceById(commandId).markProcessed(clock.now());
    }

    /**
     * TECHNICAL => retry com backoff exponencial (lease baseado em attempts atual)
     * BUSINESS/POISON => não retry (noRetryLease)
     */
    @Transactional
    public void markFailed(String commandId, String type, String message) {
        Objects.requireNonNull(commandId, "commandId");

        InboxCommandJpaEntity e = repo.getReferenceById(commandId);
        Instant now = clock.now();

        Duration lease;
        if ("TECHNICAL".equalsIgnoreCase(type)) {
            lease = leaseForAttempt(Math.max(1, e.getAttempts()));
        } else {
            lease = props.getBackoff().getNoRetryLease();
        }

        e.markFailed(type, truncate(message, 480), now, lease);
    }

    /**
     * Lease exponencial baseado em attempt (1..N), usando properties:
     * lease = min(base * 2^(attempt-1), max)
     */
    public Duration leaseForAttempt(int attempt) {
        int a = Math.max(1, attempt);

        Duration base = props.getBackoff().getBaseLease();
        Duration max = props.getBackoff().getMaxLease();

        if (base == null || base.isZero() || base.isNegative()) {
            base = Duration.ofSeconds(10);
        }
        if (max == null || max.isZero() || max.isNegative()) {
            max = Duration.ofMinutes(10);
        }

        long baseMillis = base.toMillis();
        long maxMillis = max.toMillis();

        int exp = Math.min(30, a - 1); // evita overflow em shift
        long factor = 1L << exp;       // 2^exp

        long candidate;
        if (baseMillis > 0 && factor > 0 && baseMillis > (Long.MAX_VALUE / factor)) {
            candidate = Long.MAX_VALUE;
        } else {
            candidate = baseMillis * factor;
        }

        long millis = Math.min(candidate, maxMillis);
        return Duration.ofMillis(millis);
    }

    public long recoveryDelayMs() {
        return props.getRecovery().getDelayMs();
    }

    public int recoveryBatchSize() {
        int bs = props.getRecovery().getBatchSize();
        return Math.max(1, bs);
    }

    public Duration noRetryLease() {
        return props.getBackoff().getNoRetryLease();
    }

    private int safeNextAttempt(int currentAttempts) {
        if (currentAttempts <= 0) return 1;
        if (currentAttempts >= Integer.MAX_VALUE - 1) return Integer.MAX_VALUE;
        return currentAttempts + 1;
    }

    private void validate(String commandId, String commandType, String topic, String payloadJson) {
        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is blank");
        if (commandType == null || commandType.isBlank()) throw new IllegalArgumentException("commandType is blank");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is blank");
        if (payloadJson == null || payloadJson.isBlank()) throw new IllegalArgumentException("payloadJson is blank");
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
