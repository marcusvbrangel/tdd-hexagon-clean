package com.mvbr.retailstore.checkout.domain.model;

import com.mvbr.retailstore.checkout.domain.exception.SagaDomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class CheckoutSaga {

    private final String orderId;
    private final String sagaId;
    private final String correlationId;

    private SagaStatus status;
    private SagaStep step;

    private String customerId;
    private String amount;
    private String currency;
    private String paymentMethod;

    private List<CheckoutSagaItem> items;

    private Instant deadlineAt;
    private int attemptsInventory;
    private int attemptsPayment;
    private int attemptsOrderCompletion;

    private String lastError;
    private String lastEventId;

    private boolean orderCompleted;
    private boolean inventoryReleased;
    private boolean orderCanceled;

    private CheckoutSaga(String orderId, String sagaId, String correlationId) {
        if (orderId == null || orderId.isBlank()) {
            throw new SagaDomainException("orderId cannot be null/blank");
        }
        if (sagaId == null || sagaId.isBlank()) {
            throw new SagaDomainException("sagaId cannot be null/blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new SagaDomainException("correlationId cannot be null/blank");
        }

        this.orderId = orderId;
        this.sagaId = sagaId;
        this.correlationId = correlationId;

        this.status = SagaStatus.RUNNING;
        this.step = SagaStep.STARTED;
        this.currency = "BRL";
        this.items = List.of();
        this.deadlineAt = null;
    }

    public static CheckoutSaga start(String orderId, String correlationId) {
        return new CheckoutSaga(orderId, UUID.randomUUID().toString(), correlationId);
    }

    public void onOrderPlaced(String customerId,
                              String amount,
                              String currency,
                              String paymentMethod,
                              List<CheckoutSagaItem> items,
                              Instant deadlineAt) {
        if (status != SagaStatus.RUNNING || this.step != SagaStep.STARTED) {
            return;
        }

        this.customerId = required(customerId, "customerId");
        this.amount = required(amount, "amount");
        if (currency != null && !currency.isBlank()) {
            this.currency = currency;
        }
        if (paymentMethod != null && !paymentMethod.isBlank()) {
            this.paymentMethod = paymentMethod;
        }

        this.items = sanitizeItems(items);

        this.step = SagaStep.WAIT_INVENTORY;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.attemptsInventory = 0;
        this.lastError = null;
    }

    public void onInventoryReserved(Instant deadlineAt) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_INVENTORY);
        this.step = SagaStep.WAIT_PAYMENT;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.attemptsPayment = 0;
        this.lastError = null;
    }

    public void onInventoryRejected(String reason) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_INVENTORY);
        this.status = SagaStatus.CANCELED;
        this.step = SagaStep.DONE;
        this.orderCanceled = true;
        this.lastError = reason;
        this.deadlineAt = null;
    }

    public void onPaymentAuthorized(Instant deadlineAt) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_PAYMENT);
        this.step = SagaStep.WAIT_ORDER_COMPLETION;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
        this.attemptsOrderCompletion = 0;
        this.lastError = null;
    }

    public void onPaymentDeclined(String reason) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_PAYMENT);
        this.status = SagaStatus.CANCELED;
        this.step = SagaStep.DONE;
        this.inventoryReleased = true;
        this.orderCanceled = true;
        this.lastError = reason;
        this.deadlineAt = null;
    }

    public void markOrderCompleted() {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.WAIT_ORDER_COMPLETION);
        this.orderCompleted = true;
        this.status = SagaStatus.COMPLETED;
        this.step = SagaStep.DONE;
        this.deadlineAt = null;
    }

    public void markInventoryReleased() {
        this.inventoryReleased = true;
    }

    public void markOrderCanceled(String reason) {
        this.orderCanceled = true;
        if (reason != null && !reason.isBlank()) {
            this.lastError = reason;
        }
        if (status == SagaStatus.RUNNING) {
            this.status = SagaStatus.CANCELED;
            this.step = SagaStep.DONE;
            this.deadlineAt = null;
        }
    }

    public void scheduleInventoryRetry(Instant deadlineAt) {
        ensureStep(SagaStep.WAIT_INVENTORY);
        this.attemptsInventory += 1;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
    }

    public void schedulePaymentRetry(Instant deadlineAt) {
        ensureStep(SagaStep.WAIT_PAYMENT);
        this.attemptsPayment += 1;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
    }

    public void scheduleOrderCompletionRetry(Instant deadlineAt) {
        ensureStep(SagaStep.WAIT_ORDER_COMPLETION);
        this.attemptsOrderCompletion += 1;
        this.deadlineAt = Objects.requireNonNull(deadlineAt, "deadlineAt");
    }

    public void recordLastEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        this.lastEventId = eventId;
    }

    private void ensureStep(SagaStep expected) {
        if (this.step != expected) {
            throw new SagaDomainException("Invalid step transition: expected " + expected + " but was " + step);
        }
    }

    private String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new SagaDomainException(name + " cannot be null/blank");
        }
        return value;
    }

    private List<CheckoutSagaItem> sanitizeItems(List<CheckoutSagaItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(items));
    }

    public String getOrderId() {
        return orderId;
    }

    public String getSagaId() {
        return sagaId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public SagaStep getStep() {
        return step;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public List<CheckoutSagaItem> getItems() {
        return items;
    }

    public Instant getDeadlineAt() {
        return deadlineAt;
    }

    public int getAttemptsInventory() {
        return attemptsInventory;
    }

    public int getAttemptsPayment() {
        return attemptsPayment;
    }

    public int getAttemptsOrderCompletion() {
        return attemptsOrderCompletion;
    }

    public String getLastError() {
        return lastError;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public boolean isOrderCompleted() {
        return orderCompleted;
    }

    public boolean isInventoryReleased() {
        return inventoryReleased;
    }

    public boolean isOrderCanceled() {
        return orderCanceled;
    }

    public static CheckoutSaga restore(
            String orderId,
            String sagaId,
            String correlationId,
            SagaStatus status,
            SagaStep step,
            String customerId,
            String amount,
            String currency,
            String paymentMethod,
            List<CheckoutSagaItem> items,
            Instant deadlineAt,
            int attemptsInventory,
            int attemptsPayment,
            int attemptsOrderCompletion,
            String lastError,
            String lastEventId,
            boolean orderCompleted,
            boolean inventoryReleased,
            boolean orderCanceled
    ) {
        CheckoutSaga saga = new CheckoutSaga(orderId, sagaId, correlationId);
        saga.status = Objects.requireNonNull(status, "status");
        saga.step = Objects.requireNonNull(step, "step");
        saga.customerId = customerId;
        saga.amount = amount;
        saga.currency = (currency == null || currency.isBlank()) ? "BRL" : currency;
        saga.paymentMethod = paymentMethod;
        saga.items = (items == null) ? List.of() : List.copyOf(items);
        saga.deadlineAt = deadlineAt;
        saga.attemptsInventory = attemptsInventory;
        saga.attemptsPayment = attemptsPayment;
        saga.attemptsOrderCompletion = attemptsOrderCompletion;
        saga.lastError = lastError;
        saga.lastEventId = lastEventId;
        saga.orderCompleted = orderCompleted;
        saga.inventoryReleased = inventoryReleased;
        saga.orderCanceled = orderCanceled;
        return saga;
    }
}
