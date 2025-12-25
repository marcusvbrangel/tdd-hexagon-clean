package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Repositorio Spring Data para estoque com lock pessimista.
 */
public interface JpaInventorySpringDataRepository extends JpaRepository<JpaInventoryItemEntity, String> {

    /**
     * Carrega os itens e aplica FOR UPDATE para evitar over-sell.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from JpaInventoryItemEntity i where i.productId in :productIds")
    List<JpaInventoryItemEntity> lockByProductIds(List<String> productIds);
}
