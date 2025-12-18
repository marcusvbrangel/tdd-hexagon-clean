package com.mvbr.estudo.tdd.application.port.out;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;
import com.mvbr.estudo.tdd.domain.model.Order;
import com.mvbr.estudo.tdd.domain.model.OrderId;

import java.util.Optional;

public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(OrderId orderId);

    default Order getById(OrderId id) {
        return findById(id)
                .orElseThrow(() -> new InvalidOrderException("Order not found: " + id.value()));
    }

}
