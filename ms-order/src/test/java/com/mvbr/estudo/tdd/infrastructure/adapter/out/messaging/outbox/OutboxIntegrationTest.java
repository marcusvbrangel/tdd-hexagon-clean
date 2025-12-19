package com.mvbr.estudo.tdd.infrastructure.adapter.out.messaging.outbox;

import com.mvbr.estudo.tdd.application.command.PlaceOrderCommand;
import com.mvbr.estudo.tdd.application.command.PlaceOrderItemCommand;
import com.mvbr.estudo.tdd.application.service.OrderCommandService;
import com.mvbr.estudo.tdd.application.port.out.OrderRepository;
import com.mvbr.estudo.tdd.domain.model.CustomerId;
import com.mvbr.estudo.tdd.domain.model.Money;
import com.mvbr.estudo.tdd.domain.model.Order;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "outbox.relay.enabled=false",
        "outbox.retention.enabled=false"
})
class OutboxIntegrationTest {

    @Autowired
    private OrderCommandService orderCommandService;

    @Autowired
    private OutboxJpaRepository outboxRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void cleanOutbox() {
        outboxRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist placed event into outbox with aggregate id and event id")
    void placeOrderPersistsOutbox() {
        OrderId orderId = orderCommandService.execute(samplePlaceOrder());

        List<OutboxMessageJpaEntity> messages = outboxRepository.findAll();
        assertThat(messages).hasSize(1);

        OutboxMessageJpaEntity msg = messages.getFirst();
        assertThat(msg.getAggregateId()).isEqualTo(orderId.value());
        assertThat(msg.getEventType()).isEqualTo("OrderPlacedEvent");
        assertThat(msg.getEventId()).isNotBlank();
        assertThat(msg.getOccurredAt()).isNotNull();
        assertThat(msg.getStatus()).isEqualTo(OutboxMessageJpaEntity.Status.PENDING.name());
        assertThat(msg.getRetryCount()).isZero();
        assertThat(msg.getNextAttemptAt()).isNotNull();
    }

    @Test
    @DisplayName("Should persist confirmation event into outbox")
    void confirmOrderPersistsOutbox() {
        OrderId orderId = orderCommandService.execute(samplePlaceOrder());
        outboxRepository.deleteAll(); // isola o evento de confirmação

        orderCommandService.confirm(orderId.value());

        List<OutboxMessageJpaEntity> messages = outboxRepository.findAll();
        assertThat(messages).hasSize(1);

        OutboxMessageJpaEntity msg = messages.getFirst();
        assertThat(msg.getAggregateId()).isEqualTo(orderId.value());
        assertThat(msg.getEventType()).isEqualTo("OrderConfirmedEvent");
    }

    @Test
    @DisplayName("Should persist cancel event into outbox when canceling draft")
    void cancelOrderPersistsOutbox() {
        OrderId orderId = new OrderId(UUID.randomUUID().toString());
        Order order = Order.builder()
                .withOrderId(orderId)
                .withCustomerId(new CustomerId("cust-cancel"))
                .build();
        order.addItem("prod-xyz", 1, new Money(BigDecimal.ONE));
        orderRepository.save(order);

        orderCommandService.cancel(orderId.value());

        List<OutboxMessageJpaEntity> messages = outboxRepository.findAll();
        assertThat(messages).hasSize(1);

        OutboxMessageJpaEntity msg = messages.getFirst();
        assertThat(msg.getAggregateId()).isEqualTo(orderId.value());
        assertThat(msg.getEventType()).isEqualTo("OrderCanceledEvent");
    }

    private PlaceOrderCommand samplePlaceOrder() {
        return new PlaceOrderCommand(
                "cust-123",
                List.of(new PlaceOrderItemCommand("prod-123", 2, new BigDecimal("10.00"))),
                Optional.empty()
        );
    }
}
