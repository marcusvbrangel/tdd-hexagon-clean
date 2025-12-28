package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Entidade JPA que persiste o estado da saga de checkout.
 * Usada pelo JpaCheckoutSagaRepositoryAdapter para salvar/recuperar a saga.
 */
@Entity
@Table(
        name = "checkout_saga",
        indexes = {
                @Index(name = "uk_checkout_saga_order_id", columnList = "order_id", unique = true),
                @Index(name = "uk_checkout_saga_saga_id", columnList = "saga_id", unique = true),
                @Index(name = "idx_checkout_saga_status_step", columnList = "status, step"),
                @Index(name = "idx_checkout_saga_updated_at", columnList = "updated_at")
        }
)
public class CheckoutSagaJpaEntity {

    @Id
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "saga_id", nullable = false, length = 64)
    private String sagaId;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "step", nullable = false, length = 64)
    private String step;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "amount", length = 32)
    private String amount;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "payment_method", length = 64)
    private String paymentMethod;

    @Lob
    @Column(name = "items_json")
    private String itemsJson;

    @Column(name = "order_completed", nullable = false)
    private boolean orderCompleted;

    @Column(name = "payment_captured", nullable = false)
    private boolean paymentCaptured;

    @Column(name = "inventory_committed", nullable = false)
    private boolean inventoryCommitted;

    @Column(name = "inventory_released", nullable = false)
    private boolean inventoryReleased;

    @Column(name = "order_canceled", nullable = false)
    private boolean orderCanceled;

    @Column(name = "deadline_at")
    private Instant deadlineAt;

    @Column(name = "attempts_inventory", nullable = false)
    private int attemptsInventory;

    @Column(name = "attempts_payment", nullable = false)
    private int attemptsPayment;

    @Column(name = "attempts_order_completion", nullable = false)
    private int attemptsOrderCompletion;

    @Column(name = "attempts_payment_capture", nullable = false)
    private int attemptsPaymentCapture;

    @Column(name = "attempts_inventory_commit", nullable = false)
    private int attemptsInventoryCommit;

    @Column(name = "last_error", length = 512)
    private String lastError;

    @Column(name = "last_event_id", length = 128)
    private String lastEventId;

    @Column(name = "inventory_reserve_command_id", length = 64)
    private String inventoryReserveCommandId;

    @Column(name = "payment_authorize_command_id", length = 64)
    private String paymentAuthorizeCommandId;

    @Column(name = "order_complete_command_id", length = 64)
    private String orderCompleteCommandId;

    @Column(name = "payment_capture_command_id", length = 64)
    private String paymentCaptureCommandId;

    @Column(name = "inventory_commit_command_id", length = 64)
    private String inventoryCommitCommandId;

    @Column(name = "inventory_release_command_id", length = 64)
    private String inventoryReleaseCommandId;

    @Column(name = "order_cancel_command_id", length = 64)
    private String orderCancelCommandId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /**
     * Construtor padrao exigido pelo JPA.
     */
    protected CheckoutSagaJpaEntity() {}

    /*
     * Getters e setters usados pelo JPA e pelo adapter para mapear campos.
     */
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStep() { return step; }
    public void setStep(String step) { this.step = step; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }
    public boolean isOrderCompleted() { return orderCompleted; }
    public void setOrderCompleted(boolean orderCompleted) { this.orderCompleted = orderCompleted; }
    public boolean isPaymentCaptured() { return paymentCaptured; }
    public void setPaymentCaptured(boolean paymentCaptured) { this.paymentCaptured = paymentCaptured; }
    public boolean isInventoryCommitted() { return inventoryCommitted; }
    public void setInventoryCommitted(boolean inventoryCommitted) { this.inventoryCommitted = inventoryCommitted; }
    public boolean isInventoryReleased() { return inventoryReleased; }
    public void setInventoryReleased(boolean inventoryReleased) { this.inventoryReleased = inventoryReleased; }
    public boolean isOrderCanceled() { return orderCanceled; }
    public void setOrderCanceled(boolean orderCanceled) { this.orderCanceled = orderCanceled; }
    public Instant getDeadlineAt() { return deadlineAt; }
    public void setDeadlineAt(Instant deadlineAt) { this.deadlineAt = deadlineAt; }
    public int getAttemptsInventory() { return attemptsInventory; }
    public void setAttemptsInventory(int attemptsInventory) { this.attemptsInventory = attemptsInventory; }
    public int getAttemptsPayment() { return attemptsPayment; }
    public void setAttemptsPayment(int attemptsPayment) { this.attemptsPayment = attemptsPayment; }
    public int getAttemptsOrderCompletion() { return attemptsOrderCompletion; }
    public void setAttemptsOrderCompletion(int attemptsOrderCompletion) { this.attemptsOrderCompletion = attemptsOrderCompletion; }
    public int getAttemptsPaymentCapture() { return attemptsPaymentCapture; }
    public void setAttemptsPaymentCapture(int attemptsPaymentCapture) { this.attemptsPaymentCapture = attemptsPaymentCapture; }
    public int getAttemptsInventoryCommit() { return attemptsInventoryCommit; }
    public void setAttemptsInventoryCommit(int attemptsInventoryCommit) { this.attemptsInventoryCommit = attemptsInventoryCommit; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getLastEventId() { return lastEventId; }
    public void setLastEventId(String lastEventId) { this.lastEventId = lastEventId; }
    public String getInventoryReserveCommandId() { return inventoryReserveCommandId; }
    public void setInventoryReserveCommandId(String inventoryReserveCommandId) {
        this.inventoryReserveCommandId = inventoryReserveCommandId;
    }
    public String getPaymentAuthorizeCommandId() { return paymentAuthorizeCommandId; }
    public void setPaymentAuthorizeCommandId(String paymentAuthorizeCommandId) {
        this.paymentAuthorizeCommandId = paymentAuthorizeCommandId;
    }
    public String getOrderCompleteCommandId() { return orderCompleteCommandId; }
    public void setOrderCompleteCommandId(String orderCompleteCommandId) {
        this.orderCompleteCommandId = orderCompleteCommandId;
    }
    public String getPaymentCaptureCommandId() { return paymentCaptureCommandId; }
    public void setPaymentCaptureCommandId(String paymentCaptureCommandId) {
        this.paymentCaptureCommandId = paymentCaptureCommandId;
    }
    public String getInventoryCommitCommandId() { return inventoryCommitCommandId; }
    public void setInventoryCommitCommandId(String inventoryCommitCommandId) {
        this.inventoryCommitCommandId = inventoryCommitCommandId;
    }
    public String getInventoryReleaseCommandId() { return inventoryReleaseCommandId; }
    public void setInventoryReleaseCommandId(String inventoryReleaseCommandId) {
        this.inventoryReleaseCommandId = inventoryReleaseCommandId;
    }
    public String getOrderCancelCommandId() { return orderCancelCommandId; }
    public void setOrderCancelCommandId(String orderCancelCommandId) {
        this.orderCancelCommandId = orderCancelCommandId;
    }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
