package com.mvbr.retailstore.inventory.infrastructure.adapter.in.inbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface InboxCommandJpaRepository extends JpaRepository<InboxCommandJpaEntity, String> {

    /**
     * Busca registros prontos para recovery:
     * - status IN_PROGRESS ou FAILED
     * - lockedUntil < now (lease expirou)
     * Ordenado para pegar os mais antigos primeiro.
     */
    List<InboxCommandJpaEntity> findByStatusInAndLockedUntilBeforeOrderByLockedUntilAsc(
            List<InboxCommandJpaEntity.Status> statuses,
            Instant now,
            Pageable pageable
    );
}
