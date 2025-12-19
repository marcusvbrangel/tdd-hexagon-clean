package com.mvbr.retailstore.order.domain.event;

import com.mvbr.retailstore.order.domain.model.CustomerId;
import com.mvbr.retailstore.order.domain.model.OrderId;
import com.mvbr.retailstore.order.domain.model.ProductId;

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
        return "order.placed";
    }
}