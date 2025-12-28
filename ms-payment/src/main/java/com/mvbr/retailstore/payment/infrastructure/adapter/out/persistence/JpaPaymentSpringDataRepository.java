package com.mvbr.retailstore.payment.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repositorio Spring Data para tabela payments.
 */
public interface JpaPaymentSpringDataRepository extends JpaRepository<JpaPaymentEntity, String> {

    Optional<JpaPaymentEntity> findByOrderId(String orderId);

    Optional<JpaPaymentEntity> findByProviderPaymentId(String providerPaymentId);
}
