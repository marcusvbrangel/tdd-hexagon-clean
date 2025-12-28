package com.mvbr.retailstore.order.domain.event;

import com.mvbr.retailstore.order.domain.model.CustomerId;
import com.mvbr.retailstore.order.domain.model.OrderId;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderPlacedEvent(
        String eventId,
        Instant occurredAt,
        OrderId orderId,
        CustomerId customerId,
        List<Item> items,
        String total,
        String currency
) implements DomainEvent {

    public record Item(
            String productId,
            int quantity,
            String unitPrice
    ) { }

    public static OrderPlacedEvent of(OrderId orderId,
                                      CustomerId customerId,
                                      List<Item> items,
                                      String total,
                                      String currency) {
        if (orderId == null) throw new IllegalArgumentException("OrderId cannot be null");
        if (customerId == null) throw new IllegalArgumentException("CustomerId cannot be null");
        if (items == null) throw new IllegalArgumentException("Items cannot be null");
        if (total == null || total.isBlank()) throw new IllegalArgumentException("Total cannot be null/blank");
        if (currency == null || currency.isBlank()) throw new IllegalArgumentException("Currency cannot be null/blank");

        return new OrderPlacedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                orderId,
                customerId,
                List.copyOf(items),
                total,
                currency
        );
    }

    @Override
    public String eventType() {
        return EventTypes.ORDER_PLACED;
    }
}
