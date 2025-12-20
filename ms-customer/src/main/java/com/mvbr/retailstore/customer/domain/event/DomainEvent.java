package com.mvbr.retailstore.customer.domain.event;

import java.time.Instant;

public interface DomainEvent {
    String eventId();
    Instant occurredAt();
    String eventType();
}
