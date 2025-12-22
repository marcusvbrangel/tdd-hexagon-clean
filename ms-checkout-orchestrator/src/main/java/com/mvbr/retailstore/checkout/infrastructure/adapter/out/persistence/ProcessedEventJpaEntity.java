package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEventJpaEntity {

    @Id
    @Column(name = "event_id", length = 128)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 128)
    private String eventType;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEventJpaEntity() {}

    public ProcessedEventJpaEntity(String eventId, String eventType, String orderId) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.orderId = orderId;
        this.processedAt = Instant.now();
    }

    public String getEventId() { return eventId; }
}
