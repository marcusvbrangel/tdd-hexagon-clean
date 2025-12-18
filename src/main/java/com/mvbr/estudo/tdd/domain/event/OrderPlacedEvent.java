package com.mvbr.estudo.tdd.domain.event;

import com.mvbr.estudo.tdd.domain.model.CustomerId;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import com.mvbr.estudo.tdd.domain.model.ProductId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlacedEvent(
    String eventId,
    Instant occurredAt,
    OrderId orderId,
    CustomerId customerId,
    List<ProductId> productIds
) implements DomainEvent {

    public static OrderPlacedEvent of(OrderId orderId, CustomerId customerId, List<ProductId> productIds) {
        return new OrderPlacedEvent(UUID.randomUUID().toString(),
                Instant.now(), orderId, customerId, List.copyOf(productIds));
    }

    @Override public String eventType() {
        return "OrderPlacedEvent";
    }
}