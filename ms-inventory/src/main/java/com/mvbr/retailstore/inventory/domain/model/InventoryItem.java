package com.mvbr.retailstore.inventory.domain.model;

import java.time.Instant;
import java.util.Objects;

public class InventoryItem {

    private final ProductId productId;
    private long onHand;
    private long reserved;
    private Instant updatedAt;

    public InventoryItem(ProductId productId, long onHand, long reserved, Instant updatedAt) {
        this.productId = Objects.requireNonNull(productId, "productId");
        this.onHand = onHand;
        this.reserved = reserved;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        validateInvariant();
    }

    public ProductId getProductId() { return productId; }
    public long getOnHand() { return onHand; }
    public long getReserved() { return reserved; }
    public Instant getUpdatedAt() { return updatedAt; }

    public long available() {
        return onHand - reserved;
    }

    public void reserve(Quantity qty, Instant now) {
        Objects.requireNonNull(qty, "qty");
        reserve(qty.value(), now);
    }

    public void release(Quantity qty, Instant now) {
        Objects.requireNonNull(qty, "qty");
        release(qty.value(), now);
    }

    public void commit(Quantity qty, Instant now) {
        Objects.requireNonNull(qty, "qty");
        commit(qty.value(), now);
    }

    public void reserve(long qty, Instant now) {
        validateQty(qty);
        Objects.requireNonNull(now, "now");

        if (available() < qty) {
            throw new IllegalStateException("insufficient stock");
        }

        this.reserved = Math.addExact(this.reserved, qty);
        this.updatedAt = now;
        validateInvariant();
    }

    public void release(long qty, Instant now) {
        validateQty(qty);
        Objects.requireNonNull(now, "now");

        if (this.reserved < qty) {
            throw new IllegalStateException("reserved underflow");
        }

        this.reserved = Math.subtractExact(this.reserved, qty);
        this.updatedAt = now;
        validateInvariant();
    }

    public void commit(long qty, Instant now) {
        validateQty(qty);
        Objects.requireNonNull(now, "now");

        if (this.reserved < qty) {
            throw new IllegalStateException("reserved underflow");
        }
        if (this.onHand < qty) {
            throw new IllegalStateException("onHand underflow");
        }

        this.reserved = Math.subtractExact(this.reserved, qty);
        this.onHand = Math.subtractExact(this.onHand, qty);
        this.updatedAt = now;
        validateInvariant();
    }

    public void increaseOnHand(long qty, Instant now) {
        validateQty(qty);
        Objects.requireNonNull(now, "now");

        this.onHand = Math.addExact(this.onHand, qty);
        this.updatedAt = now;
        validateInvariant();
    }

    private static void validateQty(long qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");
    }

    private void validateInvariant() {
        if (onHand < 0 || reserved < 0) {
            throw new IllegalStateException("negative stock fields");
        }
        if (reserved > onHand) {
            throw new IllegalStateException("reserved cannot exceed onHand");
        }
    }
}
