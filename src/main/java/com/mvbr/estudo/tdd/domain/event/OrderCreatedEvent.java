package com.mvbr.estudo.tdd.domain.event;

import java.util.List;

public record OrderCreatedEvent(
        String orderId,
        String customerId,
        List<String> productIds
) { }
