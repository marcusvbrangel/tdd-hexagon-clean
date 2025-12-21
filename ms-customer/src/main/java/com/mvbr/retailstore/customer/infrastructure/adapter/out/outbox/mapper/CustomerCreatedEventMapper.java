package com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.mapper;

import com.mvbr.retailstore.customer.domain.event.CustomerCreatedEvent;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.dto.CustomerCreatedEventV1;

public final class CustomerCreatedEventMapper {

    private CustomerCreatedEventMapper() {
    }

    public static CustomerCreatedEventV1 toDto(CustomerCreatedEvent event) {
        return new CustomerCreatedEventV1(
                event.eventId(),
                event.occurredAt().toString(),
                event.customerId().value()
        );
    }
}
