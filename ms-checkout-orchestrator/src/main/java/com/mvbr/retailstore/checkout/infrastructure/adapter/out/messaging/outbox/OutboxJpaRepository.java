package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;

/**
 * Repository Spring Data para mensagens da outbox.
 */
public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, Long> {

    /**
     * Busca um lote de mensagens prontas para publicar usando lock pessimista.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    List<OutboxMessageJpaEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses,
            Instant nextAttemptAt);

    /**
     * Remove mensagens publicadas antigas para controlar crescimento da tabela.
     */
    long deleteByStatusAndPublishedAtBefore(String status, Instant cutoff);
}
