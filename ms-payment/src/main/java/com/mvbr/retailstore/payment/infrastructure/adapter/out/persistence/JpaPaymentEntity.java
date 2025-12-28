package com.mvbr.retailstore.payment.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidade JPA para pagamentos.
 */
@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(name = "uk_payments_order_id", columnList = "order_id", unique = true)
        }
)
public class JpaPaymentEntity {

    @Id
    @Column(name = "payment_id", length = 64)
    private String paymentId;

    @Column(name = "provider_payment_id", length = 64, unique = true)
    private String providerPaymentId;

    @Column(name = "order_id", nullable = false, length = 64, unique = true)
    private String orderId;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency;

    @Column(name = "payment_method", length = 64)
    private String paymentMethod;

    @Column(name = "reason", length = 128)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_command_id", length = 64)
    private String lastCommandId;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    protected JpaPaymentEntity() {
    }

    public JpaPaymentEntity(String paymentId,
                            String orderId,
                            String status,
                            BigDecimal amount,
                            String currency,
                            Instant createdAt,
                            Instant updatedAt) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getPaymentId() { return paymentId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPaymentMethod() { return paymentMethod; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getLastCommandId() { return lastCommandId; }
    public String getCorrelationId() { return correlationId; }

    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }
    public void setStatus(String status) { this.status = status; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public void setReason(String reason) { this.reason = reason; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setLastCommandId(String lastCommandId) { this.lastCommandId = lastCommandId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
}
