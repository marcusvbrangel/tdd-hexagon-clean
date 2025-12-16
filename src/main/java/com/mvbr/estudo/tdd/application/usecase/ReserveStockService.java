package com.mvbr.estudo.tdd.application.usecase;

import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.application.port.out.StockGateway;
import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import com.mvbr.estudo.tdd.domain.model.Order;
import org.springframework.transaction.annotation.Transactional;

public class ReserveStockService implements ReserveStockUseCase {

    private final OrderRepository orderRepository;
    private final StockGateway stockGateway;

    public ReserveStockService(OrderRepository orderRepository, StockGateway stockGateway) {
        this.orderRepository = orderRepository;
        this.stockGateway = stockGateway;
    }

    @Override
    @Transactional
    public void execute(OrderId orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new InvalidOrderException("Order not found: " + orderId));

        stockGateway.reserve(orderId, order.getItems());
    }
}
