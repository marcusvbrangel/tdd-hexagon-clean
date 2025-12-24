package com.mvbr.retailstore.checkout.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Repository Spring Data para a entidade CheckoutSagaJpaEntity.
 * Usado pelo adapter JPA.
 */
public interface JpaCheckoutSagaSpringDataRepository extends JpaRepository<CheckoutSagaJpaEntity, String> {

    /**
     * Busca as primeiras sagas expiradas por status/etapa, em ordem de deadline.
     */
    List<CheckoutSagaJpaEntity> findTop100ByStatusAndStepInAndDeadlineAtLessThanEqualOrderByDeadlineAtAsc(
            String status,
            Collection<String> steps,
            Instant deadlineAt
    );
}
