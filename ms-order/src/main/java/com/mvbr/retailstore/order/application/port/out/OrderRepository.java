package com.mvbr.retailstore.order.application.port.out;

import com.mvbr.retailstore.order.domain.exception.InvalidOrderException;
import com.mvbr.retailstore.order.domain.model.Order;
import com.mvbr.retailstore.order.domain.model.OrderId;

import java.util.Optional;

public interface OrderRepository {

    void save(Order order);

    Optional<Order> findById(OrderId orderId);

    default Order getById(OrderId id) {
        return findById(id)
                .orElseThrow(() -> new InvalidOrderException("Order not found: " + id.value()));
    }

}
