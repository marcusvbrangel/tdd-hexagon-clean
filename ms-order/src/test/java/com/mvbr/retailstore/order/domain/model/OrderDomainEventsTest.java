package com.mvbr.retailstore.order.domain.model;

import com.mvbr.retailstore.order.domain.event.OrderCanceledEvent;
import com.mvbr.retailstore.order.domain.event.OrderConfirmedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderDomainEventsTest {

    @Test
    @DisplayName("Should emit OrderPlacedEvent and OrderConfirmedEvent in sequence")
    void confirmShouldEmitEvent() {
        Order order = Order.builder()
                .withOrderId(new OrderId("ord-confirm"))
                .withCustomerId(new CustomerId("cust-1"))
                .build();

        order.addItem("prod-1", 1, new Money(BigDecimal.TEN));
        order.place();

        // descarta evento de colocação para focar no evento de confirmação
        order.pullEvents();

        order.confirm();
        List<?> events = order.pullEvents();

        assertThat(events).singleElement().isInstanceOf(OrderConfirmedEvent.class);
        OrderConfirmedEvent event = (OrderConfirmedEvent) events.getFirst();
        assertThat(event.orderId()).isEqualTo(order.getOrderId());
        assertThat(event.customerId()).isEqualTo(order.getCustomerId());
    }

    @Test
    @DisplayName("Should emit OrderCanceledEvent when canceling from draft")
    void cancelShouldEmitEvent() {
        Order order = Order.builder()
                .withOrderId(new OrderId("ord-cancel"))
                .withCustomerId(new CustomerId("cust-2"))
                .build();

        order.addItem("prod-2", 2, new Money(new BigDecimal("5.00")));

        order.cancel();
        List<?> events = order.pullEvents();

        assertThat(events).singleElement().isInstanceOf(OrderCanceledEvent.class);
        OrderCanceledEvent event = (OrderCanceledEvent) events.getFirst();
        assertThat(event.orderId()).isEqualTo(order.getOrderId());
        assertThat(event.customerId()).isEqualTo(order.getCustomerId());
    }
}
