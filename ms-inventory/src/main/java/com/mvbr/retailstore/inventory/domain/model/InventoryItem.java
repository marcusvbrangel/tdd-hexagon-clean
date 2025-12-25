package com.mvbr.retailstore.inventory.domain.model;

import java.time.Instant;

/**
 * Entidade de dominio que representa o saldo de estoque por produto.
 * Controla onHand (fisico) e reserved (reservado por pedidos em andamento).
 */
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

    /**
     * Retorna o identificador do produto.
     */
    public ProductId getProductId() {
        return productId;
    }

    /**
     * Total fisico disponível no estoque.
     */
    public long getOnHand() {
        return onHand;
    }

    /**
     * Total reservado para pedidos ainda nao finalizados.
     */
    public long getReserved() {
        return reserved;
    }

    /**
     * Momento da ultima atualizacao do saldo.
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Calcula o estoque disponivel (onHand - reserved).
     */
    public long available() {
        return onHand - reserved;
    }

    /**
     * Reserva quantidade no estoque, mantendo invariantes.
     */
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

    /**
     * Libera quantidade previamente reservada.
     */
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

    /**
     * Garante que os campos permanecem consistentes.
     */
    private void validateNonNegative() {
        if (onHand < 0 || reserved < 0) {
            throw new IllegalStateException("negative stock fields");
        }
        if (reserved > onHand) {
            throw new IllegalStateException("reserved cannot exceed onHand");
        }
    }
}
