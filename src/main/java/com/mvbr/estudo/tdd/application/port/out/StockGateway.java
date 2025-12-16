package com.mvbr.estudo.tdd.application.port.out;

import com.mvbr.estudo.tdd.domain.model.OrderId;
import com.mvbr.estudo.tdd.domain.model.OrderItem;

import java.util.List;

public interface StockGateway {

    void reserve(OrderId orderId, List<OrderItem> items);
}
