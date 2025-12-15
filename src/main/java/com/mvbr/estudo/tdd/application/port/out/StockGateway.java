package com.mvbr.estudo.tdd.application.port.out;

import com.mvbr.estudo.tdd.domain.model.OrderItem;

import java.util.List;

public interface StockGateway {

    void reserve(String orderId, List<OrderItem> items);
}
