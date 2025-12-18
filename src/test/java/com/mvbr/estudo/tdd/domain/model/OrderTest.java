package com.mvbr.estudo.tdd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mvbr.estudo.tdd.domain.exception.DomainException;
import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

@DisplayName("Order - Domain Model Tests")
class OrderTest {

    // ========================================================
    // Step 01 - Create Order with valid data
    // ========================================================

    @Test
    @DisplayName("Should create a order with valid order Id and customer id")
    void shouldCreateOrderWithValidData() {

        // Given
        String orderId = "ord-123";
        String customerId = "cust-456";

        // When
        Order order = Order.builder()
                .withOrderId(new OrderId(orderId))
                .withCustomerId(new CustomerId(customerId))
                .build();

        // Then
        assertThat(order).isNotNull();
        assertThat(order.getOrderId()).isEqualTo(new OrderId(orderId));
        assertThat(order.getCustomerId()).isEqualTo(new CustomerId(customerId));

    }

    @Test
    @DisplayName("Should throw exception when order Id is null")
    void shouldThrowExceptionWhenOrderIdIsNull() {

        // Given
        // When/Then
        assertThatThrownBy(() -> Order.builder()
                .withOrderId(null)
                .withCustomerId(new CustomerId("cust-456"))
                .build())
                .isExactlyInstanceOf(DomainException.class)
                .hasMessage("Order ID cannot be null");

    }

    @Test
    @DisplayName("Should throw exception when order Id is blank")
    void shouldThrowExceptionWhenOrderIdIsBlank() {

        // When/Then
        assertThatThrownBy(() -> Order.builder()
                .withOrderId(new OrderId("  "))
                .withCustomerId(new CustomerId("cust-456"))
                .build())
                .isExactlyInstanceOf(InvalidOrderException.class)
                .hasMessage("Order ID cannot be blank");

    }

    @Test
    @DisplayName("Should throw exception when customer Id is null")
    void shouldThrowExceptionWhenCustomerIdIsNull() {

        // When/Then
        assertThatThrownBy(() -> Order.builder()
                .withOrderId(new OrderId("ord-123"))
                .withCustomerId(null)
                .build())
                .isExactlyInstanceOf(DomainException.class)
                .hasMessage("Customer ID cannot be null");

    }

    @Test
    @DisplayName("Should throw exception when customer Id is blank")
    void shouldThrowExceptionWhenCustomerIdIsBlank() {

        // When/Then
        assertThatThrownBy(() -> Order.builder()
                .withOrderId(new OrderId("ord-123"))
                .withCustomerId(new CustomerId("   "))
                .build())
                .isExactlyInstanceOf(InvalidOrderException.class)
                .hasMessage("Customer ID cannot be blank");

    }

    @Test
    @DisplayName("Should initialize order with DRAFT status")
    void shouldInitializeOrderWithDraftStatus() {

        // Given/When
        Order order = Order.builder()
                .withOrderId(new OrderId(UUID.randomUUID().toString()))
                .withCustomerId(new CustomerId("cust-456"))
                .build();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);

    }

    @Test
    @DisplayName("Should add item to order")
    void shouldAddItemToOrder() {

        // Given
        Order order = Order.builder()
                .withOrderId(new OrderId("ord-123"))
                .withCustomerId(new CustomerId("cust-456"))
                .build();
        String productId = "prod-789";
        int quantity = 2;
        Money price = new Money(new BigDecimal("50.00"));

        // When
        order.addItem(productId, quantity, price);

        // Then
        assertThat(order.getItems()).hasSize(1);

        OrderItem item = order.getItems().get(0);
        assertThat(item.getProductId()).isEqualTo(productId);
        assertThat(item.getQuantity()).isEqualTo(quantity);
        assertThat(item.getPrice()).isEqualTo(price);

    }

    @Test
    @DisplayName("Should restore order with items and discount")
    void shouldRestoreOrderWithItemsAndDiscount() {

        // When
        Order order = Order.builder()
                .withOrderId(new OrderId("ord-restore"))
                .withCustomerId(new CustomerId("cust-001"))
                .build();
        order.addItem("prod-1", 2, new Money(new BigDecimal("10.00")));
        order.addItem("prod-2", 1, new Money(new BigDecimal("20.00")));
        order.applyDiscount(new Money(new BigDecimal("5.00")));
        order.place();
        order.confirm();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getTotal()).isEqualTo(new Money(new BigDecimal("35.00")));
    }

    // PASSO 8: 🔴 Validar Quantidade do Item














}
