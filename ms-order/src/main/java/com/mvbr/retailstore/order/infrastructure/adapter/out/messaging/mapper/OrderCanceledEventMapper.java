package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.retailstore.order.domain.event.OrderCanceledEvent;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderCanceledEventV1;

public final class OrderCanceledEventMapper {

    private OrderCanceledEventMapper() {
    }

    public static OrderCanceledEventV1 toDto(OrderCanceledEvent event) {
        return new OrderCanceledEventV1(
                event.eventId(),
                event.occurredAt().toString(),
                event.orderId().value(),
                event.customerId().value()
        );
    }
}
