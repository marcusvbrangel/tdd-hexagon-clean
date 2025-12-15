package com.mvbr.estudo.tdd.domain.model;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class Order {

    private final String orderId;
    private final String customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private BigDecimal total;

    private BigDecimal discount;

    public Order(String orderId, String customerId) {

        validateOrderId(orderId);
        validateCustomerId(customerId);

        this.orderId = orderId;
        this.customerId = customerId;
        this.status = OrderStatus.DRAFT;
        this.items = new ArrayList<>();
        this.discount = BigDecimal.ZERO;
        this.total = BigDecimal.ZERO;
        
    }

    public void addItem(String productId, int quantity, BigDecimal price) {
        if (status != OrderStatus.DRAFT) {
            throw new InvalidOrderException("Can only add items to DRAFT orders");
        }

        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be greater than zero");
        }

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidOrderException("Price must be greater than zero");
        }

        OrderItem item = new OrderItem(productId, quantity, price);
        this.items.add(item);
        recalculateTotal();
    }

    // ✅ Método para calcular subtotal (sem desconto)
    private BigDecimal calculateSubtotal() {
        return items.stream()
                .map(OrderItem::getSubTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ✅ Modificar para incluir desconto
    private void recalculateTotal() {
        BigDecimal subtotal = calculateSubtotal();
        this.total = subtotal.subtract(this.discount);
    }

    public void confirm() {
        if (items.isEmpty()) {
            throw new InvalidOrderException("Cannot confirm order without items");
        }

        if (status != OrderStatus.DRAFT) {
            throw new InvalidOrderException("Only DRAFT orders can be confirmed");
        }

        this.status = OrderStatus.CONFIRMED;
    }

    // ✅ Novo método: cancelar pedido
    public void cancel() {
        if (status == OrderStatus.COMPLETED) {
            throw new InvalidOrderException("Cannot cancel completed order");
        }

        this.status = OrderStatus.CANCELLED;
    }

    // ✅ Novo método: completar pedido
    public void complete() {
        if (status != OrderStatus.CONFIRMED) {
            throw new InvalidOrderException("Only CONFIRMED orders can be completed");
        }

        this.status = OrderStatus.COMPLETED;
    }

    public void applyDiscount(BigDecimal discount) {
        // Validar desconto negativo
        if (discount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidOrderException("Discount cannot be negative");
        }

        // Calcular subtotal
        BigDecimal subtotal = calculateSubtotal();

        // Validar desconto maior que subtotal
        if (discount.compareTo(subtotal) > 0) {
            throw new InvalidOrderException("Discount cannot be greater than subtotal");
        }

        this.discount = discount;
        recalculateTotal();
    }

    private static void validateCustomerId(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new InvalidOrderException("Customer ID cannot be null or blank");
        }
    }

    private static void validateOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new InvalidOrderException("Order ID cannot be null or blank");
        }
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public BigDecimal getTotal() {
        return total;
    }

}

