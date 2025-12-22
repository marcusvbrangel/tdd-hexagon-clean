package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.retailstore.order.domain.event.OrderCompletedEvent;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderCompletedEventV1;

public final class OrderCompletedEventMapper {

    private OrderCompletedEventMapper() {
    }

    public static OrderCompletedEventV1 toDto(OrderCompletedEvent event) {
        return new OrderCompletedEventV1(
                event.eventId(),
                event.occurredAt().toString(),
                event.orderId().value(),
                event.customerId().value()
        );
    }
}
