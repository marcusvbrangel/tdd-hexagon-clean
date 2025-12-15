package com.mvbr.estudo.tdd.application.usecase;

import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.domain.model.Order;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public class ListOrdersUseCase {

    private final OrderRepository orderRepository;

    public ListOrdersUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional(readOnly = true)
    public List<Order> execute() {
        return orderRepository.findAll();
    }
}
