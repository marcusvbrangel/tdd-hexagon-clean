package com.mvbr.estudo.tdd.application.usecase;

import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.domain.model.Order;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class GetOrderUseCase {

    private final OrderRepository orderRepository;

    public GetOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public Optional<Order> execute(String orderId) {
        return orderRepository.findById(orderId);
    }
}
