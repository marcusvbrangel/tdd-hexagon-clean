package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

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

    @Column(name = "order_completed", nullable = false)
    private boolean orderCompleted;

    @Column(name = "inventory_released", nullable = false)
    private boolean inventoryReleased;

    @Column(name = "order_canceled", nullable = false)
    private boolean orderCanceled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CheckoutSagaJpaEntity() {}

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
    public boolean isOrderCompleted() { return orderCompleted; }
    public void setOrderCompleted(boolean orderCompleted) { this.orderCompleted = orderCompleted; }
    public boolean isInventoryReleased() { return inventoryReleased; }
    public void setInventoryReleased(boolean inventoryReleased) { this.inventoryReleased = inventoryReleased; }
    public boolean isOrderCanceled() { return orderCanceled; }
    public void setOrderCanceled(boolean orderCanceled) { this.orderCanceled = orderCanceled; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
