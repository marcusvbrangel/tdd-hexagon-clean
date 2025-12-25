package com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.time.Instant;
import java.util.List;

/**
 * Repositorio JPA da outbox com lock pessimista para consumo seguro.
 */
public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, Long> {

    /**
     * Busca lote de mensagens pendentes/failed prontas para envio.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    List<OutboxMessageJpaEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses,
            Instant nextAttemptAt);

    /**
     * Remove mensagens publicadas antigas para manter a tabela enxuta.
     */
    long deleteByStatusAndPublishedAtBefore(String status, Instant cutoff);
}
