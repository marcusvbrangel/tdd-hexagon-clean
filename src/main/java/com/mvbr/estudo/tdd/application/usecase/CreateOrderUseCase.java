package com.mvbr.estudo.tdd.application.usecase;

import com.mvbr.estudo.tdd.application.port.in.CreateOrderCommand;
import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.domain.event.OrderCreatedEvent;
import com.mvbr.estudo.tdd.domain.model.Order;

import java.util.UUID;

public class CreateOrderUseCase {

    private final OrderRepository orderRepository;

    public CreateOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public String execute(CreateOrderCommand orderCommand) {

        // identidade e decisao do usecase...
        String orderId = UUID.randomUUID().toString();

        // criacao do aggregate root...
        Order order = new Order(orderId, orderCommand.customerId());

        // orquestra comportamento do dominio...
        orderCommand.items().forEach(item -> order.addItem(item.productId(), item.quantity(), item.price()));

        // regras opcionais delegada ao dominio...
        if (orderCommand.discount() != null) {
            order.applyDiscount(orderCommand.discount());
        }

        // persistencia...
        orderRepository.save(order);

        // evento de dominio (placeholder para futura publicação)
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId,
                orderCommand.customerId(),
                orderCommand.items().stream().map(item -> item.productId()).toList()
        );
        // TODO: publicar evento quando EventPublisher estiver implementado

        // retorna o minimo (opcional mas util)...
        return orderId;

    }

}
