package com.mvbr.estudo.tdd.domain.event;

import com.mvbr.estudo.tdd.domain.model.CustomerId;
import com.mvbr.estudo.tdd.domain.model.OrderId;

import java.util.List;

public record OrderCreatedEvent(
        OrderId orderId,
        CustomerId customerId,
        List<String> productIds
) { }
