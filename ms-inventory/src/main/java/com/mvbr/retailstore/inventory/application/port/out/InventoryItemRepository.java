package com.mvbr.retailstore.inventory.application.port.out;

import com.mvbr.retailstore.inventory.domain.model.InventoryItem;

import java.util.List;
import java.util.Optional;

/**
 * Porta de persistencia para itens de estoque.
 */
public interface InventoryItemRepository {

    /**
     * Carrega e bloqueia itens por produto (FOR UPDATE).
     */
    List<InventoryItem> lockByProductIds(List<String> productIds);

    /**
     * Busca um item por produto sem lock.
     */
    Optional<InventoryItem> findByProductId(String productId);

    /**
     * Persiste alteracoes no item de estoque.
     */
    InventoryItem save(InventoryItem item);
}
