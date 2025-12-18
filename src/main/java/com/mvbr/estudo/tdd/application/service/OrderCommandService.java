package com.mvbr.estudo.tdd.application.service;

import com.mvbr.estudo.tdd.application.command.PlaceOrderCommand;
import com.mvbr.estudo.tdd.application.port.in.CancelOrderUseCase;
import com.mvbr.estudo.tdd.application.port.in.ConfirmOrderUseCase;
import com.mvbr.estudo.tdd.application.port.in.PlaceOrderUseCase;
import com.mvbr.estudo.tdd.application.port.out.EventPublisher;
import com.mvbr.estudo.tdd.application.port.out.OrderIdGenerator;
import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;
import com.mvbr.estudo.tdd.domain.model.CustomerId;
import com.mvbr.estudo.tdd.domain.model.Money;
import com.mvbr.estudo.tdd.domain.model.Order;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import org.springframework.transaction.annotation.Transactional;

public class OrderCommandService implements
        PlaceOrderUseCase,
        ConfirmOrderUseCase,
        CancelOrderUseCase {

    private final OrderRepository orderRepository;
    private final OrderIdGenerator orderIdGenerator;;
    private final EventPublisher eventPublisher;

    public OrderCommandService(OrderRepository orderRepository,
                               OrderIdGenerator orderIdGenerator,
                               EventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.orderIdGenerator = orderIdGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public OrderId execute(PlaceOrderCommand placeOrderCommand) {

        // identidade e decisao do usecase...
        OrderId orderId = orderIdGenerator.nextId();
        CustomerId customerId = new CustomerId(placeOrderCommand.customerId());

        // criacao do aggregate root...
        Order order = Order.builder()
                .withOrderId(orderId)
                .withCustomerId(customerId)
                .build();

        // orquestra comportamento do dominio...
        placeOrderCommand.items().forEach(item -> order.addItem(
                item.productId(),
                item.quantity(),
                new Money(item.price())
        ));

        // regras opcionais delegada ao dominio...
        placeOrderCommand.discount()
                .map(Money::new)
                .ifPresent(order::applyDiscount);

        order.place();

        // persistencia...
        orderRepository.save(order);

        // eventos de dominio...
        order.pullEvents().forEach(eventPublisher::publish);

        // retorna o minimo (opcional mas util)...
        return orderId;

    }

    @Override
    @Transactional
    public Order confirm(String orderId) {
        Order order = orderRepository.findById(new OrderId(orderId))
                .orElseThrow(() -> new InvalidOrderException("Order not found: " + orderId));

        order.confirm();
        orderRepository.save(order);
        order.pullEvents().forEach(eventPublisher::publish);
        return order;
    }

    @Override
    @Transactional
    public Order cancel(String orderId) {
        Order order = orderRepository.findById(new OrderId(orderId))
                .orElseThrow(() -> new InvalidOrderException("Order not found: " + orderId));

        order.cancel();
        orderRepository.save(order);
        order.pullEvents().forEach(eventPublisher::publish);
        return order;
    }




}












