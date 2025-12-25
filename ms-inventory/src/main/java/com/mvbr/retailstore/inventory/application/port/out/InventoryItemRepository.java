package com.mvbr.retailstore.inventory.application.port.out;

import com.mvbr.retailstore.inventory.domain.model.InventoryItem;

import java.util.List;
import java.util.Optional;

public interface InventoryItemRepository {

    List<InventoryItem> lockByProductIds(List<String> productIds);

    Optional<InventoryItem> findByProductId(String productId);

    InventoryItem save(InventoryItem item);
}
