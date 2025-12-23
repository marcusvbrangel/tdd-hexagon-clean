package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface JpaCheckoutSagaSpringDataRepository extends JpaRepository<CheckoutSagaJpaEntity, String> {

    List<CheckoutSagaJpaEntity> findTop100ByStatusAndStepInAndDeadlineAtLessThanEqualOrderByDeadlineAtAsc(
            String status,
            Collection<String> steps,
            Instant deadlineAt
    );
}
