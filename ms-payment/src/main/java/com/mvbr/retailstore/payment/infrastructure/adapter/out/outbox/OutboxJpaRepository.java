package com.mvbr.retailstore.payment.infrastructure.adapter.out.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Repositorio Spring Data para tabela outbox_messages.
 */
public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, Long> {

    List<OutboxMessageJpaEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> status,
            Instant nextAttemptAt
    );

    long deleteByStatusAndPublishedAtBefore(String status, Instant cutoff);
}
