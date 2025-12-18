package com.mvbr.estudo.tdd.domain.model;

import com.mvbr.estudo.tdd.domain.event.DomainEvent;
import com.mvbr.estudo.tdd.domain.event.OrderCanceledEvent;
import com.mvbr.estudo.tdd.domain.event.OrderConfirmedEvent;
import com.mvbr.estudo.tdd.domain.event.OrderPlacedEvent;
import com.mvbr.estudo.tdd.domain.exception.DomainException;

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
    private final List<DomainEvent> events;

    public static Builder builder() {
        return new Builder();
    }

    private Order(Builder builder) {
        if (builder.orderId == null) {
            throw new DomainException("Order ID cannot be null");
        }
        if (builder.customerId == null) {
            throw new DomainException("Customer ID cannot be null");
        }

        this.orderId = builder.orderId;
        this.customerId = builder.customerId;

        this.status = OrderStatus.DRAFT;
        this.items = new ArrayList<>();

        this.discount = Money.zero();
        this.total = Money.zero(); // calculado a partir de items + discount

        this.events = new ArrayList<>();

    }

    public void addItem(String productId, int quantity, Money price) {
        ensureDraft();

        // OrderItem já valida productId/quantity/price, então não duplica validação aqui.
        items.add(new OrderItem(productId, quantity, price));

        recalculateTotal();
    }

    public void applyDiscount(Money discount) {
        ensureDraft();

        if (discount == null) {
            throw new DomainException("Discount cannot be null");
        }

        Money subtotal = calculateSubtotal();

        // permite desconto == subtotal (total pode ser 0)
        if (discount.isGreaterThan(subtotal)) {
            throw new DomainException("Discount cannot be greater than subtotal");
        }

        this.discount = discount;
        recalculateTotal();
    }

    public void place() {
        ensureStatus(OrderStatus.DRAFT);

        if (items.isEmpty()) {
            throw new DomainException("Cannot place an order with no items");
        }

        // Total já está sempre recalculado ao adicionar item/aplicar desconto,
        // mas isso blinda caso alguém mexa no código futuramente.
        recalculateTotal();

        this.status = OrderStatus.PLACED;
        registerEvent(OrderPlacedEvent.of(orderId, customerId, productIds()));
    }

    public void confirm() {
        ensureStatus(OrderStatus.PLACED);
        this.status = OrderStatus.CONFIRMED;
        registerEvent(OrderConfirmedEvent.of(orderId, customerId));
    }

    public void cancel() {
        ensureStatus(OrderStatus.DRAFT);
        this.status = OrderStatus.CANCELED;
        registerEvent(OrderCanceledEvent.of(orderId, customerId));
    }

    private Money calculateSubtotal() {
        return items.stream()
                .map(OrderItem::getSubTotal)
                .reduce(Money.zero(), Money::add);
    }

    private void recalculateTotal() {
        Money subtotal = calculateSubtotal();

        // Money.subtract já impede resultado negativo, então isso blinda total < 0.
        Money newTotal = subtotal.subtract(this.discount);

        // opcionalmente: garantimos consistência (nunca null)
        this.total = Objects.requireNonNull(newTotal, "Total cannot be null");
    }

    private void ensureDraft() {
        ensureStatus(OrderStatus.DRAFT);
    }

    private void ensureStatus(OrderStatus expected) {
        if (this.status != expected) {
            throw new DomainException("Invalid status transition: expected " + expected + " but was " + status);
        }
    }

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

        // reidratação: inserir itens sem regras de DRAFT
        order.items.addAll(items);

        order.discount = discount;
        order.recalculateTotal();

        // status do banco é “fonte da verdade” na reidratação
        order.status = status;

        return order;
    }

    public void restoreStatusFromPersistence(OrderStatus status) {
        if (status == null) {
            throw new DomainException("Status cannot be null");
        }
        this.status = status;
    }

    public List<DomainEvent> pullEvents() {
        List<DomainEvent> copy = List.copyOf(events);
        events.clear();
        return copy;
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

    private void registerEvent(DomainEvent event) {
        if (event == null) {
            throw new DomainException("Event cannot be null");
        }
        events.add(event);
    }

    private List<ProductId> productIds() {
        return items.stream()
                .map(OrderItem::getProductId)
                .map(ProductId::new)
                .toList();
    }

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


    /*
    ======================================================

    Requisito no domínio:

        Pra isso funcionar bem, seu Order.place() precisa:

        permitir apenas se status == DRAFT

        exigir items não vazio

        setar status = PLACED

        E aí:

        confirm() só funciona se PLACED

        cancel() só funciona se PLACED (ou DRAFT também, se você quiser)

        -----------------------------------------------------------
        Diagrama de estados / transições:

            DRAFT -> PLACED

            PLACED -> CONFIRMED

            CONFIRMED -> COMPLETED

            DRAFT -> CANCELED

    =====================================================
     */

}
