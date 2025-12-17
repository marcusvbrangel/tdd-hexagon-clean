package com.mvbr.estudo.tdd.domain.model;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Order {

    private final OrderId orderId;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private Money total;
    private Money discount;

    public static Builder builder() {
        return new Builder();
    }

    private Order(Builder builder) {
        if (builder.orderId == null) {
            throw new InvalidOrderException("Order ID cannot be blank");
        }
        if (builder.customerId == null) {
            throw new InvalidOrderException("Customer ID cannot be blank");
        }
        this.orderId = builder.orderId;
        this.customerId = builder.customerId;
        this.status = Objects.requireNonNullElse(builder.status, OrderStatus.DRAFT);
        this.items = new ArrayList<>(builder.items);
        this.total = Objects.requireNonNullElse(builder.total, Money.zero());
        this.discount = Objects.requireNonNullElse(builder.discount, Money.zero());
        recalculateTotal();
    }

    public void addItem(String productId, int quantity, Money price) {
        if (status != OrderStatus.DRAFT) {
            throw new InvalidOrderException("Can only add items to DRAFT orders");
        }

        validateItemData(productId, quantity, price);

        OrderItem item = new OrderItem(productId, quantity, price);
        this.items.add(item);
        recalculateTotal();
    }

    private static void validateItemData(String productId, int quantity, Money price) {
        if (quantity <= 0) {
            throw new InvalidOrderException("Quantity must be greater than zero");
        }

        if (price == null || price.isZeroOrNegative()) {
            throw new InvalidOrderException("Price must be greater than zero");
        }

        if (productId == null || productId.isBlank()) {
            throw new InvalidOrderException("Product ID cannot be blank");
        }
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
        if (discount == null) {
            throw new InvalidOrderException("Discount cannot be null");
        }
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

    public static class Builder {

        private OrderId orderId;
        private CustomerId customerId;
        private OrderStatus status = OrderStatus.DRAFT;
        private final List<OrderItem> items = new ArrayList<>();
        private Money total = Money.zero();
        private Money discount = Money.zero();

        public Builder() {
        }

        public Builder withOrderId(OrderId orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder withCustomerId(CustomerId customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder addItem(String productId, int quantity, Money price) {
            validateItemData(productId, quantity, price);
            this.items.add(new OrderItem(productId, quantity, price));
            return this;
        }

        public Order build() {
            return new Order(this);
        }
    }

}
