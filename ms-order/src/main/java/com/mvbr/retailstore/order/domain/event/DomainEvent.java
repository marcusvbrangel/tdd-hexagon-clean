package com.mvbr.retailstore.order.domain.event;

import java.time.Instant;

public interface DomainEvent {
    String eventId();
    Instant occurredAt();
    String eventType();
}