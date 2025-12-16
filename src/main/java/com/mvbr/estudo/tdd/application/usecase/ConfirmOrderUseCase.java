package com.mvbr.estudo.tdd.application.usecase;

import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import com.mvbr.estudo.tdd.domain.model.Order;
import org.springframework.transaction.annotation.Transactional;

public class ConfirmOrderUseCase {

    private final OrderRepository orderRepository;

    public ConfirmOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order execute(String orderId) {
        Order order = orderRepository.findById(new OrderId(orderId))
                .orElseThrow(() -> new InvalidOrderException("Order not found: " + orderId));

        order.confirm();
        orderRepository.save(order);
        return order;
    }
}
