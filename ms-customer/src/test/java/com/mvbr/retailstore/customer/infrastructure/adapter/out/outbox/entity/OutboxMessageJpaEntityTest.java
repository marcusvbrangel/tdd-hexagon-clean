package com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxMessageJpaEntityTest {

    @Test
    void constructorSetsDefaults() {
        Instant occurredAt = Instant.parse("2025-01-01T10:00:00Z");

        OutboxMessageJpaEntity entity = new OutboxMessageJpaEntity(
                "event-1",
                "Customer",
                "cust-1",
                "CustomerCreated",
                "customer.created",
                "{\"eventId\":\"event-1\"}",
                "{\"eventId\":\"event-1\"}",
                occurredAt
        );

        assertEquals("event-1", entity.getEventId());
        assertEquals("Customer", entity.getAggregateType());
        assertEquals("cust-1", entity.getAggregateId());
        assertEquals("CustomerCreated", entity.getEventType());
        assertEquals("customer.created", entity.getTopic());
        assertEquals("PENDING", entity.getStatus());
        assertEquals(0, entity.getRetryCount());
        assertEquals(occurredAt, entity.getOccurredAt());
        assertNotNull(entity.getCreatedAt());
        assertEquals(entity.getCreatedAt(), entity.getNextAttemptAt());
    }

    @Test
    void markFailedAndPublishedUpdateState() {
        OutboxMessageJpaEntity entity = new OutboxMessageJpaEntity(
                "event-2",
                "Customer",
                "cust-2",
                "CustomerChanged",
                "customer.changed",
                "{\"eventId\":\"event-2\"}",
                "{\"eventId\":\"event-2\"}",
                Instant.parse("2025-01-02T10:00:00Z")
        );

        entity.markInProgress();
        assertEquals("IN_PROGRESS", entity.getStatus());

        Instant beforeFail = Instant.now();
        entity.markFailed("boom");
        assertEquals("FAILED", entity.getStatus());
        assertEquals("boom", entity.getPublishErrorDetails());
        assertEquals(1, entity.getRetryCount());
        assertTrue(entity.getNextAttemptAt().isAfter(beforeFail));

        entity.markPublished();
        assertEquals("PUBLISHED", entity.getStatus());
        assertNotNull(entity.getPublishedAt());
        assertEquals(entity.getPublishedAt(), entity.getNextAttemptAt());
    }
}
