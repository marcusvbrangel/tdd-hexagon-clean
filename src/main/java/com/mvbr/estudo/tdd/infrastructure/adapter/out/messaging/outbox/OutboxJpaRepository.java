package com.mvbr.estudo.tdd.infrastructure.adapter.out.messaging.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxJpaRepository extends JpaRepository<OutboxMessageJpaEntity, Long> {
    List<OutboxMessageJpaEntity> findTop100ByStatusOrderByCreatedAtAsc(String status);
}
