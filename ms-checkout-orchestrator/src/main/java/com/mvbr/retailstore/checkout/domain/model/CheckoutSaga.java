package com.mvbr.retailstore.checkout.domain.model;

import com.mvbr.retailstore.checkout.domain.exception.SagaDomainException;

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
    }

    public static CheckoutSaga start(String orderId, String correlationId) {
        return new CheckoutSaga(orderId, UUID.randomUUID().toString(), correlationId);
    }

    public void onOrderPlaced(String customerId, String amount, String currency) {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        if (this.step != SagaStep.STARTED) {
            return;
        }

        this.customerId = required(customerId, "customerId");
        this.amount = required(amount, "amount");
        if (currency != null && !currency.isBlank()) {
            this.currency = currency;
        }

        this.step = SagaStep.INVENTORY_RESERVE_PENDING;
    }

    public void onInventoryReserved() {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.INVENTORY_RESERVE_PENDING);
        this.step = SagaStep.PAYMENT_AUTHORIZE_PENDING;
    }

    public void onInventoryRejected() {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        this.inventoryReleased = true;
        this.status = SagaStatus.COMPENSATING;
        this.step = SagaStep.COMPENSATE_ORDER_CANCEL_PENDING;
    }

    public void onPaymentAuthorized() {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.PAYMENT_AUTHORIZE_PENDING);
        this.step = SagaStep.ORDER_COMPLETE_PENDING;
    }

    public void onPaymentDeclined() {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        ensureStep(SagaStep.PAYMENT_AUTHORIZE_PENDING);
        this.status = SagaStatus.COMPENSATING;
        this.step = SagaStep.COMPENSATE_INVENTORY_RELEASE_PENDING;
    }

    public void markOrderCompleted() {
        if (status != SagaStatus.RUNNING) {
            return;
        }
        this.orderCompleted = true;
        this.status = SagaStatus.COMPLETED;
        this.step = SagaStep.DONE;
    }

    public void markInventoryReleased() {
        if (status != SagaStatus.COMPENSATING) {
            return;
        }
        this.inventoryReleased = true;
        tryFinishCompensation();
    }

    public void markOrderCanceled() {
        if (status != SagaStatus.COMPENSATING) {
            return;
        }
        this.orderCanceled = true;
        tryFinishCompensation();
    }

    private void tryFinishCompensation() {
        if (inventoryReleased && orderCanceled) {
            this.status = SagaStatus.CANCELLED;
            this.step = SagaStep.DONE;
        } else {
            this.step = SagaStep.WAITING_COMPENSATIONS;
        }
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
        saga.orderCompleted = orderCompleted;
        saga.inventoryReleased = inventoryReleased;
        saga.orderCanceled = orderCanceled;
        return saga;
    }
}
