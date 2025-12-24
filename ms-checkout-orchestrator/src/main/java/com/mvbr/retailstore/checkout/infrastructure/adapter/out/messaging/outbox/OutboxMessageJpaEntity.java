package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Entidade JPA para mensagens do padrao outbox (publicacao confiavel).
 * Criada pelo OutboxCommandPublisherAdapter e publicada pelo OutboxRelay.
 */
@Entity
@Table(
        name = "outbox_messages",
        indexes = {
                @Index(name = "idx_outbox_status_created", columnList = "status, created_at"),
                @Index(name = "idx_outbox_status_next_attempt", columnList = "status, next_attempt_at"),
                @Index(name = "uk_outbox_event_id", columnList = "event_id", unique = true)
        }
)
public class OutboxMessageJpaEntity {

    /**
     * Estados do processamento da outbox.
     */
    public enum Status { PENDING, IN_PROGRESS, PUBLISHED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Lob
    @Column(name = "payload_json", nullable = false)
    private String payloadJson;

    @Lob
    @Column(name = "headers_json", nullable = false)
    private String headersJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Version
    private long version;

    /**
     * Construtor padrao exigido pelo JPA.
     */
    protected OutboxMessageJpaEntity() {}

    /**
     * Cria uma nova mensagem com status PENDING pronta para publicacao.
     */
    public OutboxMessageJpaEntity(String eventId,
                                  String aggregateType,
                                  String aggregateId,
                                  String eventType,
                                  String topic,
                                  String payloadJson,
                                  String headersJson,
                                  Instant occurredAt) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.payloadJson = payloadJson;
        this.headersJson = headersJson;
        this.occurredAt = occurredAt;
        this.status = Status.PENDING.name();
        this.createdAt = Instant.now();
        this.nextAttemptAt = this.createdAt;
        this.retryCount = 0;
    }

    /*
     * Getters usados pelo OutboxRelay e pelo JPA.
     */
    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getAggregateType() { return aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getTopic() { return topic; }
    public String getPayloadJson() { return payloadJson; }
    public String getHeadersJson() { return headersJson; }
    public String getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }

    /**
     * Marca a mensagem como em processamento antes do envio ao Kafka.
     */
    public void markInProgress() {
        this.status = Status.IN_PROGRESS.name();
    }

    /**
     * Marca como publicada com timestamp e limpa erros.
     */
    public void markPublished() {
        this.status = Status.PUBLISHED.name();
        this.publishedAt = Instant.now();
        this.lastError = null;
        this.nextAttemptAt = this.publishedAt;
    }

    /**
     * Marca como falha e agenda novo envio com backoff.
     */
    public void markFailed(String error) {
        this.status = Status.FAILED.name();
        this.lastError = error;
        this.retryCount = this.retryCount + 1;
        this.nextAttemptAt = computeBackoff();
    }

    /**
     * Calcula o backoff exponencial baseado no numero de falhas.
     */
    private Instant computeBackoff() {
        long baseSeconds = 5;
        long maxSeconds = 3600;
        long delaySeconds = (long) Math.min(maxSeconds, baseSeconds * Math.pow(2, Math.max(0, retryCount - 1)));
        return Instant.now().plusSeconds(delaySeconds);
    }
}
