package com.mvbr.estudo.tdd.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

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
        Order order = new Order(orderId, customerId);

        // Then
        assertThat(order).isNotNull();
        assertThat(order.getOrderId()).isEqualTo(orderId);
        assertThat(order.getCustomerId()).isEqualTo(customerId);

    }

    @Test
    @DisplayName("Should throw exception when order Id is null")
    void shouldThrowExceptionWhenOrderIdIsNull() {

        // Given
        String orderId = null;
        String customerId = "cust-456";

        // When/Then
        assertThatThrownBy(() -> new Order(orderId, customerId))
                .isExactlyInstanceOf(InvalidOrderException.class)
                .hasMessage("Order ID cannot be null or blank");

    }

    @Test
    @DisplayName("Should throw exception when order Id is blank")
    void shouldThrowExceptionWhenOrderIdIsBlank() {

        // When/Then
        assertThatThrownBy(() -> new Order("  ", "cust-456"))
                .isExactlyInstanceOf(InvalidOrderException.class)
                .hasMessage("Order ID cannot be null or blank");

    }

    @Test
    @DisplayName("Should throw exception when customer Id is null")
    void shouldThrowExceptionWhenCustomerIdIsNull() {

        // When/Then
        assertThatThrownBy(() -> new Order("ord-123", null))
                .isExactlyInstanceOf(InvalidOrderException.class)
                .hasMessage("Customer ID cannot be null or blank");

    }

    @Test
    @DisplayName("Should throw exception when customer Id is blank")
    void shouldThrowExceptionWhenCustomerIdIsBlank() {

        // When/Then
        assertThatThrownBy(() -> new Order("ord-123", "   "))
                .isExactlyInstanceOf(InvalidOrderException.class)
                .hasMessage("Customer ID cannot be null or blank");

    }

    @Test
    @DisplayName("Should initialize order with DRAFT status")
    void shouldInitializeOrderWithDraftStatus() {

        // Given/When
        Order order = new Order("ord-124", "cust-456");

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DRAFT);

    }

    @Test
    @DisplayName("Should add item to order")
    void shouldAddItemToOrder() {

        // Given
        Order order = new Order("ord-123", "cust-456");
        String productId = "prod-789";
        int quantity = 2;
        BigDecimal price = new BigDecimal("50.00");

        // When
        order.addItem(productId, quantity, price);

        // Then
        assertThat(order.getItems()).hasSize(1);

        OrderItem item = order.getItems().get(0);
        assertThat(item.getProductId()).isEqualTo(productId);
        assertThat(item.getQuantity()).isEqualTo(quantity);
        assertThat(item.getPrice()).isEqualTo(price);

    }

    // PASSO 8: 🔴 Validar Quantidade do Item














}
