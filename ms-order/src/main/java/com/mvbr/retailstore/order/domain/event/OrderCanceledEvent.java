package com.mvbr.retailstore.order.domain.event;

import com.mvbr.retailstore.order.domain.model.CustomerId;
import com.mvbr.retailstore.order.domain.model.OrderId;

import java.time.Instant;
import java.util.UUID;

public record OrderCanceledEvent(
        String eventId,
        Instant occurredAt,
        OrderId orderId,
        CustomerId customerId
) implements DomainEvent {

    public static OrderCanceledEvent of(OrderId orderId, CustomerId customerId) {
        return new OrderCanceledEvent(UUID.randomUUID().toString(), Instant.now(), orderId, customerId);
    }

    @Override
    public String eventType() {
        return "order.canceled";
    }
}
