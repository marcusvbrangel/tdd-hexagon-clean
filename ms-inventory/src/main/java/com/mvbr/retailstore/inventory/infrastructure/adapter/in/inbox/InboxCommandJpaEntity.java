package com.mvbr.retailstore.inventory.infrastructure.adapter.in.inbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(
        name = "inbox_commands",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inbox_command_id", columnNames = "command_id")
        },
        indexes = {
                @Index(name = "idx_inbox_status_locked", columnList = "status, locked_until"),
                @Index(name = "idx_inbox_received_at", columnList = "received_at")
        }
)
public class InboxCommandJpaEntity {

    public enum Status {
        IN_PROGRESS,
        PROCESSED,
        FAILED
    }

    @Id
    @Column(name = "command_id", nullable = false, length = 128)
    private String commandId;

    @Column(name = "command_type", nullable = false, length = 64)
    private String commandType;

    @Column(name = "record_key", length = 256)
    private String recordKey;

    @Column(name = "topic", nullable = false, length = 200)
    private String topic;

    @Column(name = "partition_id", nullable = false)
    private int partition;

    @Column(name = "offset_id", nullable = false)
    private long offset;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "failure_type", length = 32)
    private String failureType; // BUSINESS | TECHNICAL | POISON

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    // ====== Saga context (para replay interno) ======
    @Column(name = "saga_id", length = 128)
    private String sagaId;

    @Column(name = "correlation_id", length = 128)
    private String correlationId;

    @Column(name = "causation_id", length = 128)
    private String causationId;

    @Column(name = "saga_name", length = 128)
    private String sagaName;

    @Column(name = "saga_step", length = 64)
    private String sagaStep;

    @Column(name = "aggregate_type", length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 128)
    private String aggregateId;

    // ====== Payload original (para replay) ======
    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    protected InboxCommandJpaEntity() { }

    private InboxCommandJpaEntity(String commandId,
                                  String commandType,
                                  String recordKey,
                                  String topic,
                                  int partition,
                                  long offset,
                                  Status status,
                                  Instant receivedAt,
                                  Instant lockedUntil,
                                  int attempts,
                                  String payloadJson,
                                  String sagaId,
                                  String correlationId,
                                  String causationId,
                                  String sagaName,
                                  String sagaStep,
                                  String aggregateType,
                                  String aggregateId) {

        this.commandId = commandId;
        this.commandType = commandType;
        this.recordKey = recordKey;
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.status = status;
        this.receivedAt = receivedAt;
        this.lockedUntil = lockedUntil;
        this.attempts = attempts;
        this.payloadJson = payloadJson;

        this.sagaId = sagaId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.sagaName = sagaName;
        this.sagaStep = sagaStep;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
    }

    public static InboxCommandJpaEntity started(String commandId,
                                                String commandType,
                                                String recordKey,
                                                String topic,
                                                int partition,
                                                long offset,
                                                Instant now,
                                                Duration lease,
                                                String payloadJson,
                                                String sagaId,
                                                String correlationId,
                                                String causationId,
                                                String sagaName,
                                                String sagaStep,
                                                String aggregateType,
                                                String aggregateId) {

        if (commandId == null || commandId.isBlank()) throw new IllegalArgumentException("commandId is blank");
        if (commandType == null || commandType.isBlank()) throw new IllegalArgumentException("commandType is blank");
        if (topic == null || topic.isBlank()) throw new IllegalArgumentException("topic is blank");
        if (now == null) throw new IllegalArgumentException("now is null");
        if (lease == null || lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("lease must be > 0");
        if (payloadJson == null || payloadJson.isBlank()) throw new IllegalArgumentException("payloadJson is blank");

        return new InboxCommandJpaEntity(
                commandId,
                commandType,
                recordKey,
                topic,
                partition,
                offset,
                Status.IN_PROGRESS,
                now,
                now.plus(lease),
                1,
                payloadJson,
                sagaId,
                correlationId,
                causationId,
                sagaName,
                sagaStep,
                aggregateType,
                aggregateId
        );
    }

    public String getCommandId() { return commandId; }
    public String getCommandType() { return commandType; }
    public String getPayloadJson() { return payloadJson; }
    public String getRecordKey() { return recordKey; }
    public String getTopic() { return topic; }
    public int getPartition() { return partition; }
    public long getOffset() { return offset; }
    public Status getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getLockedUntil() { return lockedUntil; }
    public int getAttempts() { return attempts; }

    public String getSagaId() { return sagaId; }
    public String getCorrelationId() { return correlationId; }
    public String getCausationId() { return causationId; }
    public String getSagaName() { return sagaName; }
    public String getSagaStep() { return sagaStep; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }

    public boolean isProcessed() {
        return status == Status.PROCESSED;
    }

    public boolean isLocked(Instant now) {
        if (now == null) return false;
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void start(Instant now, Duration lease) {
        if (now == null) throw new IllegalArgumentException("now is null");
        if (lease == null || lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("lease must be > 0");

        this.status = Status.IN_PROGRESS;
        this.lockedUntil = now.plus(lease);
        this.attempts = this.attempts + 1;
    }

    public void markProcessed(Instant now) {
        if (now == null) throw new IllegalArgumentException("now is null");

        this.status = Status.PROCESSED;
        this.processedAt = now;
        this.lockedUntil = null;
        this.failureType = null;
        this.failureMessage = null;
    }

    public void markFailed(String type, String message, Instant now, Duration lease) {
        if (now == null) throw new IllegalArgumentException("now is null");
        if (lease == null || lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("lease must be > 0");

        this.status = Status.FAILED;
        this.processedAt = now;
        this.failureType = type;
        this.failureMessage = message;
        this.lockedUntil = now.plus(lease);
    }

    public String getFailureType() { return failureType; }
    public String getFailureMessage() { return failureMessage; }
}
