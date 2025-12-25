package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaProcessedMessageSpringDataRepository extends JpaRepository<JpaProcessedMessageEntity, String> {
}
