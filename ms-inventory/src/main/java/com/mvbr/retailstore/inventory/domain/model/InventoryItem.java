package com.mvbr.retailstore.inventory.domain.model;

import java.time.Instant;

public class InventoryItem {

    private final ProductId productId;
    private long onHand;
    private long reserved;
    private Instant updatedAt;

    public InventoryItem(ProductId productId, long onHand, long reserved, Instant updatedAt) {
        this.productId = productId;
        this.onHand = onHand;
        this.reserved = reserved;
        this.updatedAt = updatedAt;
        validateNonNegative();
    }

    public ProductId getProductId() {
        return productId;
    }

    public long getOnHand() {
        return onHand;
    }

    public long getReserved() {
        return reserved;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long available() {
        return onHand - reserved;
    }

    public void reserve(long qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
        if (available() < qty) {
            throw new IllegalStateException("insufficient stock");
        }
        this.reserved += qty;
        this.updatedAt = Instant.now();
        validateNonNegative();
    }

    public void release(long qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be > 0");
        }
        if (this.reserved < qty) {
            throw new IllegalStateException("reserved underflow");
        }
        this.reserved -= qty;
        this.updatedAt = Instant.now();
        validateNonNegative();
    }

    private void validateNonNegative() {
        if (onHand < 0 || reserved < 0) {
            throw new IllegalStateException("negative stock fields");
        }
        if (reserved > onHand) {
            throw new IllegalStateException("reserved cannot exceed onHand");
        }
    }
}
