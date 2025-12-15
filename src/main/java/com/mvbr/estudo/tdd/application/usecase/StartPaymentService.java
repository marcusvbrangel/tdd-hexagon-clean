package com.mvbr.estudo.tdd.application.usecase;

import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.application.port.out.PaymentGateway;
import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;
import com.mvbr.estudo.tdd.domain.model.Order;
import org.springframework.transaction.annotation.Transactional;

public class StartPaymentService implements StartPaymentUseCase {

    private final OrderRepository orderRepository;
    private final PaymentGateway paymentGateway;

    public StartPaymentService(OrderRepository orderRepository, PaymentGateway paymentGateway) {
        this.orderRepository = orderRepository;
        this.paymentGateway = paymentGateway;
    }

    @Override
    @Transactional
    public void execute(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new InvalidOrderException("Order not found: " + orderId));

        paymentGateway.startPayment(orderId, order.getTotal());
    }
}
