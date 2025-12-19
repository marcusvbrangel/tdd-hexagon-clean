package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.retailstore.order.domain.event.OrderPlacedEvent;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;

public final class OrderPlacedEventMapper {

    private OrderPlacedEventMapper() {
    }

    public static OrderPlacedEventV1 toDto(OrderPlacedEvent event) {
        return new OrderPlacedEventV1(
                event.eventId(),
                event.occurredAt().toString(),
                event.orderId().value(),
                event.customerId().value(),
                event.productIds().stream()
                        .map(productId -> productId.value())
                        .toList()
        );
    }
}
