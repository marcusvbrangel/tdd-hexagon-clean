package com.mvbr.retailstore.inventory.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Entidade JPA para o saldo de estoque por produto.
 */
@Entity
@Table(name = "inventory_items")
public class JpaInventoryItemEntity {

    @Id
    @Column(name = "product_id", length = 64)
    private String productId;

    @Column(name = "on_hand", nullable = false)
    private long onHand;

    @Column(name = "reserved", nullable = false)
    private long reserved;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JpaInventoryItemEntity() {
    }

    public JpaInventoryItemEntity(String productId, long onHand, long reserved, Instant updatedAt) {
        this.productId = productId;
        this.onHand = onHand;
        this.reserved = reserved;
        this.updatedAt = updatedAt;
    }

    public String getProductId() { return productId; }
    public long getOnHand() { return onHand; }
    public long getReserved() { return reserved; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setOnHand(long onHand) { this.onHand = onHand; }
    public void setReserved(long reserved) { this.reserved = reserved; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
