package com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.mapper;

import com.mvbr.retailstore.customer.domain.event.CustomerChangedEvent;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.dto.CustomerChangedEventV1;

public final class CustomerChangedEventMapper {

    private CustomerChangedEventMapper() {
    }

    public static CustomerChangedEventV1 toDto(CustomerChangedEvent event) {
        return new CustomerChangedEventV1(
                event.eventId(),
                event.occurredAt().toString(),
                event.customerId().value()
        );
    }
}
