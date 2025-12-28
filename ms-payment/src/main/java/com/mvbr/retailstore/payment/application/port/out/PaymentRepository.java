package com.mvbr.retailstore.payment.application.port.out;

import com.mvbr.retailstore.payment.domain.model.Payment;

import java.util.Optional;

/**
 * Porta de persistencia para pagamentos.
 */
public interface PaymentRepository {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    Payment save(Payment payment);
}
