package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositorio Spring Data para tabela processed_messages.
 */
public interface JpaProcessedMessageSpringDataRepository extends JpaRepository<JpaProcessedMessageEntity, String> {
}
