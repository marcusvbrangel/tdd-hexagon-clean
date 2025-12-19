package com.mvbr.retailstore.order.application.port.out;

import com.mvbr.retailstore.order.domain.model.OrderId;

public interface OrderIdGenerator {
    OrderId nextId();
}
