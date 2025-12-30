package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.inventory.domain.model.InventoryItem;
import com.mvbr.retailstore.inventory.domain.model.ProductId;

import java.util.Objects;

public final class JpaInventoryItemMapper {

    private JpaInventoryItemMapper() {}

    public static InventoryItem toDomain(JpaInventoryItemEntity e) {
        Objects.requireNonNull(e, "entity");
        return new InventoryItem(
                new ProductId(e.getProductId()),
                e.getOnHand(),
                e.getReserved(),
                e.getUpdatedAt()
        );
    }

    public static JpaInventoryItemEntity toJpa(InventoryItem d) {
        Objects.requireNonNull(d, "domain");
        return new JpaInventoryItemEntity(
                d.getProductId().value(),
                d.getOnHand(),
                d.getReserved(),
                d.getUpdatedAt()
        );
    }

    public static void copyToJpa(InventoryItem d, JpaInventoryItemEntity e) {
        Objects.requireNonNull(d, "domain");
        Objects.requireNonNull(e, "entity");
        e.setOnHand(d.getOnHand());
        e.setReserved(d.getReserved());
        e.setUpdatedAt(d.getUpdatedAt());
    }
}
