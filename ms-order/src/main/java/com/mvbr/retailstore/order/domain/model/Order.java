package com.mvbr.retailstore.order.domain.model;

import com.mvbr.retailstore.order.domain.event.DomainEvent;
import com.mvbr.retailstore.order.domain.event.OrderCanceledEvent;
import com.mvbr.retailstore.order.domain.event.OrderCompletedEvent;
import com.mvbr.retailstore.order.domain.event.OrderConfirmedEvent;
import com.mvbr.retailstore.order.domain.event.OrderPlacedEvent;
import com.mvbr.retailstore.order.domain.exception.DomainException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Order {

    private final OrderId orderId;
    private final CustomerId customerId;
    private OrderStatus status;
    private final List<OrderItem> items;
    private Money total;
    private Money discount;
    private final List<DomainEvent> events;

    public static Builder builder() {
        return new Builder();
    }

    private Order(Builder builder) {
        if (builder.orderId == null) throw new DomainException("Order ID cannot be null");
        if (builder.customerId == null) throw new DomainException("Customer ID cannot be null");

        this.orderId = builder.orderId;
        this.customerId = builder.customerId;

        this.status = OrderStatus.DRAFT;
        this.items = new ArrayList<>();

        this.discount = Money.zero();
        this.total = Money.zero();

        this.events = new ArrayList<>();
    }

    // ============================
    // Comportamentos do domínio
    // ============================

    public void addItem(String productId, int quantity, Money price) {
        ensureDraft();
        items.add(new OrderItem(productId, quantity, price));
        recalculateTotal();
    }

    public void applyDiscount(Money discount) {
        ensureDraft();

        if (discount == null) throw new DomainException("Discount cannot be null");

        Money subtotal = calculateSubtotal();
        if (discount.isGreaterThan(subtotal)) {
            throw new DomainException("Discount cannot be greater than subtotal");
        }

        this.discount = discount;
        recalculateTotal();
    }

    /**
     * Coloca o pedido e inicia a saga (via evento OrderPlaced).
     */
    public void place() {
        ensureStatus(OrderStatus.DRAFT);

        if (items.isEmpty()) {
            throw new DomainException("Cannot place an order with no items");
        }

        recalculateTotal();

        this.status = OrderStatus.PLACED;

        registerEvent(OrderPlacedEvent.of(orderId, customerId, placedItemsSnapshot()));
    }

    /**
     * Confirma o pedido quando o orquestrador concluir o checkout com sucesso.
     */
    public void confirm() {
        ensureStatus(OrderStatus.PLACED);
        this.status = OrderStatus.CONFIRMED;
        registerEvent(OrderConfirmedEvent.of(orderId, customerId));
    }

    /**
     * Cancela o pedido:
     * - antes de colocar (DRAFT), ou
     * - após colocar, quando a saga falhar/expirar (PLACED).
     */
    public void cancel() {
        ensureStatusIn(OrderStatus.DRAFT, OrderStatus.PLACED);
        this.status = OrderStatus.CANCELED;
        registerEvent(OrderCanceledEvent.of(orderId, customerId));
    }

    /**
     * Finaliza o pedido por completo (pós-checkout):
     * pago + estoque efetivado + envio concluído + nota fiscal emitida/enviada.
     */
    public void complete() {
        ensureStatus(OrderStatus.CONFIRMED);
        this.status = OrderStatus.COMPLETED;
        registerEvent(OrderCompletedEvent.of(orderId, customerId));
    }

    // ============================
    // Cálculos
    // ============================

    private Money calculateSubtotal() {
        return items.stream()
                .map(OrderItem::getSubTotal)
                .reduce(Money.zero(), Money::add);
    }

    private void recalculateTotal() {
        Money subtotal = calculateSubtotal();
        Money newTotal = subtotal.subtract(this.discount);
        this.total = Objects.requireNonNull(newTotal, "Total cannot be null");
    }

    // ============================
    // Guards / invariantes
    // ============================

    private void ensureDraft() {
        ensureStatus(OrderStatus.DRAFT);
    }

    private void ensureStatus(OrderStatus expected) {
        if (this.status != expected) {
            throw new DomainException("Invalid status transition: expected " + expected + " but was " + status);
        }
    }

    private void ensureStatusIn(OrderStatus... allowed) {
        for (OrderStatus st : allowed) {
            if (this.status == st) return;
        }
        throw new DomainException("Invalid status transition: allowed " + Arrays.toString(allowed) + " but was " + status);
    }

    private List<OrderPlacedEvent.Item> placedItemsSnapshot() {
        // Snapshot "de integração": SKU + qty + unitPrice
        return items.stream()
                .map(i -> new OrderPlacedEvent.Item(
                        i.getProductId(),
                        i.getQuantity(),
                        i.getPrice().toString()
                ))
                .toList();
    }

    // ============================
    // Reidratação
    // ============================

    public static Order restore(
            OrderId orderId,
            CustomerId customerId,
            OrderStatus status,
            List<OrderItem> items,
            Money discount
    ) {
        if (orderId == null) throw new DomainException("Order ID cannot be null");
        if (customerId == null) throw new DomainException("Customer ID cannot be null");
        if (status == null) throw new DomainException("Order status cannot be null");
        if (items == null) throw new DomainException("Order items cannot be null");
        if (discount == null) discount = Money.zero();

        Order order = Order.builder()
                .withOrderId(orderId)
                .withCustomerId(customerId)
                .build();

        order.items.addAll(items);
        order.discount = discount;
        order.recalculateTotal();
        order.status = status;

        return order;
    }

    public void restoreStatusFromPersistence(OrderStatus status) {
        if (status == null) throw new DomainException("Status cannot be null");
        this.status = status;
    }

    // ============================
    // Eventos
    // ============================

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> copy = List.copyOf(events);
        events.clear();
        return copy;
    }

    private void registerEvent(DomainEvent event) {
        if (event == null) throw new DomainException("Event cannot be null");
        events.add(event);
    }

    // ============================
    // Getters
    // ============================

    public OrderId getOrderId() { return orderId; }

    public CustomerId getCustomerId() { return customerId; }

    public OrderStatus getStatus() { return status; }

    public List<OrderItem> getItems() { return List.copyOf(items); }

    public Money getDiscount() { return discount; }

    public Money getTotal() { return total; }

    // ============================
    // Builder
    // ============================

    public static class Builder {
        private OrderId orderId;
        private CustomerId customerId;

        public Builder withOrderId(OrderId orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder withCustomerId(CustomerId customerId) {
            this.customerId = customerId;
            return this;
        }

        public Order build() {
            return new Order(this);
        }
    }
}
