package com.mvbr.retailstore.inventory.infrastructure.adapter.out.outbox.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
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
     *
     * IMPORTANTE:
     * - Usa lock pessimista pra evitar dois relays publicarem o mesmo registro.
     * - lock.timeout=0 => se a linha estiver lockada por outra transação, não espera (skip "na marra").
     * - Pageable limita o batch size de forma real (sem subList).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    List<OutboxMessageJpaEntity> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses,
            Instant nextAttemptAt,
            Pageable pageable
    );

    /**
     * Remove mensagens publicadas antigas para manter a tabela enxuta.
     */
    long deleteByStatusAndPublishedAtBefore(String status, Instant cutoff);
}
