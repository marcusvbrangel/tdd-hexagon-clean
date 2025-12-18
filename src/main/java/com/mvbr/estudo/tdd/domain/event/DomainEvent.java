package com.mvbr.estudo.tdd.domain.event;

import java.time.Instant;

public interface DomainEvent {
    String eventId();
    Instant occurredAt();
    String eventType();
}