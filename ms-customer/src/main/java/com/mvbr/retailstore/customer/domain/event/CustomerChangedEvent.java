package com.mvbr.retailstore.customer.domain.event;

import com.mvbr.retailstore.customer.domain.model.CustomerId;
import com.mvbr.retailstore.customer.domain.exception.DomainException;

import java.time.Instant;
import java.util.UUID;

public record CustomerChangedEvent(String eventId, Instant occurredAt, CustomerId customerId)
        implements DomainEvent {

    public CustomerChangedEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new DomainException("Event id is required");
        }
        if (occurredAt == null) {
            throw new DomainException("Occurred at is required");
        }
        if (customerId == null) {
            throw new DomainException("Customer id is required");
        }
    }

    public static CustomerChangedEvent of(CustomerId customerId, Instant now) {

        return new CustomerChangedEvent(UUID.randomUUID().toString(), now, customerId);

    }

    @Override
    public String eventType() {
        return "customer.changed";
    }

}
