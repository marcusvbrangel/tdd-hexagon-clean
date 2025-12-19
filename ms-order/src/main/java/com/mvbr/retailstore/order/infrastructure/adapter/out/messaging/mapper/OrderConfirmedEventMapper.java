package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.retailstore.order.domain.event.OrderConfirmedEvent;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderConfirmedEventV1;

public final class OrderConfirmedEventMapper {

    private OrderConfirmedEventMapper() {
    }

    public static OrderConfirmedEventV1 toDto(OrderConfirmedEvent event) {
        return new OrderConfirmedEventV1(
                event.eventId(),
                event.occurredAt().toString(),
                event.orderId().value(),
                event.customerId().value()
        );
    }
}
