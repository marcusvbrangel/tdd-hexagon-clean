package com.mvbr.retailstore.order.infrastructure.adapter.out.idgenerator;

import com.mvbr.retailstore.order.application.port.out.OrderIdGenerator;
import com.mvbr.retailstore.order.domain.model.OrderId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidOrderIdGenerator implements OrderIdGenerator {

    @Override
    public OrderId nextId() {
        return new OrderId(UUID.randomUUID().toString());
    }

}