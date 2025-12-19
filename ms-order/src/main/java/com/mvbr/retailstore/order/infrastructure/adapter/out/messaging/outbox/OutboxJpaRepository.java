package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

import java.time.Instant;
import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    List<OutboxMessageJpaEntity> findTop100ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            List<String> statuses,
            Instant nextAttemptAt);

    long deleteByStatusAndPublishedAtBefore(String status, Instant cutoff);
}
