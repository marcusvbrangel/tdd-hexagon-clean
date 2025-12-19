package com.mvbr.estudo.tdd.domain.event;

import com.mvbr.estudo.tdd.domain.model.CustomerId;
import com.mvbr.estudo.tdd.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

public record OrderConfirmedEvent(
        String eventId,
        Instant occurredAt,
        OrderId orderId,
        CustomerId customerId
) implements DomainEvent {

    public static OrderConfirmedEvent of(OrderId orderId, CustomerId customerId) {
        return new OrderConfirmedEvent(UUID.randomUUID().toString(), Instant.now(), orderId, customerId);
    }

    @Override
    public String eventType() {
        return "order.confirmed";
    }
}
