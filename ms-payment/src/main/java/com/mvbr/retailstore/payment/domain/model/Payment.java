package com.mvbr.retailstore.payment.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entidade de dominio que representa um pagamento.
 */
public class Payment {

    private final PaymentId paymentId;
    private final String providerPaymentId;
    private final OrderId orderId;
    private final CustomerId customerId;
    private final BigDecimal amount;
    private final String currency;
    private final String paymentMethod;
    private PaymentStatus status;
    private String reason;
    private final Instant createdAt;
    private Instant updatedAt;
    private String lastCommandId;
    private String correlationId;

    public Payment(PaymentId paymentId,
                   String providerPaymentId,
                   OrderId orderId,
                   CustomerId customerId,
                   BigDecimal amount,
                   String currency,
                   String paymentMethod,
                   PaymentStatus status,
                   String reason,
                   Instant createdAt,
                   Instant updatedAt,
                   String lastCommandId,
                   String correlationId) {
        this.paymentId = paymentId;
        this.providerPaymentId = providerPaymentId;
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastCommandId = lastCommandId;
        this.correlationId = correlationId;
    }

    public PaymentId getPaymentId() {
        return paymentId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public OrderId getOrderId() {
        return orderId;
    }

    public CustomerId getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getLastCommandId() {
        return lastCommandId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    /**
     * Indica se o pagamento ja foi finalizado.
     */
    public boolean isFinalized() {
        return status == PaymentStatus.AUTHORIZED
                || status == PaymentStatus.DECLINED
                || status == PaymentStatus.CAPTURED
                || status == PaymentStatus.FAILED;
    }

    /**
     * Marca o pagamento como autorizado.
     */
    public void markAuthorized() {
        this.status = PaymentStatus.AUTHORIZED;
        this.reason = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Marca o pagamento como recusado com motivo.
     */
    public void markDeclined(String reason) {
        this.status = PaymentStatus.DECLINED;
        this.reason = reason;
        this.updatedAt = Instant.now();
    }

    /**
     * Marca o pagamento como capturado/settled.
     */
    public void markCaptured() {
        this.status = PaymentStatus.CAPTURED;
        this.reason = null;
        this.updatedAt = Instant.now();
    }

    /**
     * Marca o pagamento como falho.
     */
    public void markFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.reason = reason;
        this.updatedAt = Instant.now();
    }

    /**
     * Atualiza o ultimo commandId processado.
     */
    public void updateLastCommandId(String commandId) {
        this.lastCommandId = commandId;
    }

    /**
     * Atualiza o correlationId da saga.
     */
    public void updateCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}
