package com.mvbr.retailstore.payment.domain.model;

/**
 * Estados possiveis de um pagamento.
 */
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    DECLINED,
    CAPTURED,
    FAILED
}
