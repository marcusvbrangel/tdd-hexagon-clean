package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.order.application.command.PlaceOrderCommand;
import com.mvbr.retailstore.order.application.command.PlaceOrderItemCommand;
import com.mvbr.retailstore.order.application.service.OrderCommandService;
import com.mvbr.retailstore.order.application.port.out.OrderRepository;
import com.mvbr.retailstore.order.domain.model.CustomerId;
import com.mvbr.retailstore.order.domain.model.Money;
import com.mvbr.retailstore.order.domain.model.Order;
import com.mvbr.retailstore.order.domain.model.OrderId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "outbox.relay.enabled=false",
        "outbox.retention.enabled=false"
})
class OutboxIntegrationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        assertThat(msg.getEventType()).isEqualTo("OrderPlaced");
        assertThat(msg.getTopic()).isEqualTo("order.placed");
        assertThat(msg.getEventId()).isNotBlank();
        assertThat(msg.getOccurredAt()).isNotNull();
        assertThat(msg.getHeadersJson()).contains("schemaVersion", "eventType", "order-service");
        assertThat(msg.getStatus()).isEqualTo(OutboxMessageJpaEntity.Status.PENDING.name());
        assertThat(msg.getRetryCount()).isZero();
        assertThat(msg.getNextAttemptAt()).isNotNull();

        Map<String, Object> payload = parsePayload(msg);
        assertThat(payload.get("orderId")).isEqualTo(orderId.value());
        assertThat(payload.get("customerId")).isEqualTo("cust-123");
        @SuppressWarnings("unchecked")
        List<String> productIds = (List<String>) payload.get("productIds");
        assertThat(productIds).containsExactly("prod-123");
        assertThat(msg.getPayloadJson()).doesNotContain("\"value\"");
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
        assertThat(msg.getEventType()).isEqualTo("OrderConfirmed");
        assertThat(msg.getTopic()).isEqualTo("order.confirmed");

        Map<String, Object> payload = parsePayload(msg);
        assertThat(payload.get("orderId")).isEqualTo(orderId.value());
        assertThat(payload.get("customerId")).isEqualTo("cust-123");
        assertThat(msg.getPayloadJson()).doesNotContain("\"value\"");
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
        assertThat(msg.getEventType()).isEqualTo("OrderCanceled");
        assertThat(msg.getTopic()).isEqualTo("order.canceled");

        Map<String, Object> payload = parsePayload(msg);
        assertThat(payload.get("orderId")).isEqualTo(orderId.value());
        assertThat(payload.get("customerId")).isEqualTo("cust-2");
        assertThat(msg.getPayloadJson()).doesNotContain("\"value\"");
    }

    private PlaceOrderCommand samplePlaceOrder() {
        return new PlaceOrderCommand(
                "cust-123",
                List.of(new PlaceOrderItemCommand("prod-123", 2, new BigDecimal("10.00"))),
                Optional.empty()
        );
    }

    private Map<String, Object> parsePayload(OutboxMessageJpaEntity msg) {
        try {
            return objectMapper.readValue(msg.getPayloadJson(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new AssertionError("Failed to parse payload JSON", e);
        }
    }
}
