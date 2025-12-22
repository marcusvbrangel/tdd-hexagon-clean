package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaCheckoutSagaSpringDataRepository extends JpaRepository<CheckoutSagaJpaEntity, String> {}
