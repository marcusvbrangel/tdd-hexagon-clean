package com.mvbr.estudo.tdd.domain.model;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;

import java.util.ArrayList;
import java.util.List;

public class Order {

    private final OrderId orderId;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private Money total;
    private Money discount;

    public Order(OrderId orderId, CustomerId customerId) {
        if (orderId == null) {
            throw new InvalidOrderException("Order ID cannot be blank");
        }
        if (customerId == null) {
            throw new InvalidOrderException("Customer ID cannot be blank");
        }
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = OrderStatus.DRAFT;
        this.items = new ArrayList<>();
        this.discount = Money.zero();
        this.total = Money.zero();
        
    }

    public void addItem(String productId, int quantity, Money price) {
        if (status != OrderStatus.DRAFT) {
            throw new InvalidOrderException("Can only add items to DRAFT orders");
        }

        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be greater than zero");
        }

        if (price == null || price.isZeroOrNegative()) {
            throw new InvalidOrderException("Price must be greater than zero");
        }

        OrderItem item = new OrderItem(productId, quantity, price);
        this.items.add(item);
        recalculateTotal();
    }

    // ✅ Método para calcular subtotal (sem desconto)
    private Money calculateSubtotal() {
        return items.stream()
                .map(OrderItem::getSubTotal)
                .reduce(Money.zero(), Money::add);
    }

    // ✅ Modificar para incluir desconto
    private void recalculateTotal() {
        Money subtotal = calculateSubtotal();
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

    public void applyDiscount(Money discount) {
        // Validar desconto negativo
        if (discount.isNegative()) {
            throw new InvalidOrderException("Discount cannot be negative");
        }

        Money subtotal = calculateSubtotal();
        if (discount.isGreaterThan(subtotal)) {
            throw new InvalidOrderException("Discount cannot be greater than subtotal");
        }

        this.discount = discount;
        recalculateTotal();
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public Money getDiscount() {
        return discount;
    }

    public Money getTotal() {
        return total;
    }

}
