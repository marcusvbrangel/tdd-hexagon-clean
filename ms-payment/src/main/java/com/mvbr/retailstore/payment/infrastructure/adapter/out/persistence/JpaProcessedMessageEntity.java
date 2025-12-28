package com.mvbr.retailstore.payment.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entidade JPA que registra comandos ja processados (idempotencia).
 */
@Entity
@Table(name = "processed_messages")
public class JpaProcessedMessageEntity {

    @Id
    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(name = "message_type", nullable = false, length = 64)
    private String messageType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected JpaProcessedMessageEntity() {
    }

    public JpaProcessedMessageEntity(String messageId, String messageType, String aggregateId, Instant processedAt) {
        this.messageId = messageId;
        this.messageType = messageType;
        this.aggregateId = aggregateId;
        this.processedAt = processedAt;
    }

    public String getMessageId() { return messageId; }
}
