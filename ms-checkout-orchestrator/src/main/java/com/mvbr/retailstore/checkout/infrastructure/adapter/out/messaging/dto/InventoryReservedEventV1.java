package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto;

public record InventoryReservedEventV1(
        String eventId,
        String occurredAt,
        String orderId
) {}
