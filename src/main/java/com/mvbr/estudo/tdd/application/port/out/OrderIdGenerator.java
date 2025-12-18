package com.mvbr.estudo.tdd.application.port.out;

import com.mvbr.estudo.tdd.domain.model.OrderId;

public interface OrderIdGenerator {
    OrderId nextId();
}
