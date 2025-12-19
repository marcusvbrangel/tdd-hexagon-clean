package com.mvbr.estudo.tdd.infrastructure.adapter.out.idgenerator;

import com.mvbr.estudo.tdd.application.port.out.OrderIdGenerator;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidOrderIdGenerator implements OrderIdGenerator {

    @Override
    public OrderId nextId() {
        return new OrderId(UUID.randomUUID().toString());
    }

}