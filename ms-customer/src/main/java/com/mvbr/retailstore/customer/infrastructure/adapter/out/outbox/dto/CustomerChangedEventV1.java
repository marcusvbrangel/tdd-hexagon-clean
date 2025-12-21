package com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.dto;

public record CustomerChangedEventV1(
        String eventId,
        String occurredAt,
        String customerId
) {
}
