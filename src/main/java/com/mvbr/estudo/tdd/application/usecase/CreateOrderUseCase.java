package com.mvbr.estudo.tdd.application.usecase;

import com.mvbr.estudo.tdd.application.port.in.CreateOrderCommand;
import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.domain.event.OrderCreatedEvent;
import com.mvbr.estudo.tdd.domain.model.CustomerId;
import com.mvbr.estudo.tdd.domain.model.Money;
import com.mvbr.estudo.tdd.domain.model.Order;
import com.mvbr.estudo.tdd.domain.model.OrderId;

import java.util.UUID;

public class CreateOrderUseCase {

    private final OrderRepository orderRepository;

    public CreateOrderUseCase(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderId execute(CreateOrderCommand orderCommand) {

        // identidade e decisao do usecase...
        OrderId orderId = new OrderId(UUID.randomUUID().toString());
        CustomerId customerId = new CustomerId(orderCommand.customerId());

        // criacao do aggregate root...
        Order order = new Order(orderId, customerId);

        // orquestra comportamento do dominio...
        orderCommand.items().forEach(item -> order.addItem(
                item.productId(),
                item.quantity(),
                new Money(item.price())
        ));

        // regras opcionais delegada ao dominio...
        if (orderCommand.discount() != null) {
            order.applyDiscount(new Money(orderCommand.discount()));
        }

        // persistencia...
        orderRepository.save(order);

        // evento de dominio (placeholder para futura publicação)
        OrderCreatedEvent event = new OrderCreatedEvent(
                orderId,
                customerId,
                orderCommand.items().stream().map(item -> item.productId()).toList()
        );
        // TODO: publicar evento quando EventPublisher estiver implementado

        // retorna o minimo (opcional mas util)...
        return orderId;

    }

    /*
    =================================================================================================
    Papel correto do CreateOrderUseCase no seu cenário

Com o seu Order atual, o UseCase deve:

✔️ Receber a intenção (CreateOrderCommand)

✔️ Criar o Aggregate Root (Order)

✔️ Delegar validações e invariantes ao domínio

✔️ Orquestrar a adição de itens

✔️ Persistir via port (OrderRepository)

❌ Não validar regra de negócio

❌ Não conhecer HTTP, DTO, JPA, Kafka

👉 Ele é orquestrador, não “executor de regra”.
    =================================================================================================
     */

}
