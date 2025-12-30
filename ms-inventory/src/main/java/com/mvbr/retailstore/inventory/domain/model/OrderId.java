package com.mvbr.retailstore.inventory.domain.model;

/**
 * Identificador imutavel do pedido associado a uma reserva de estoque.
 */
public record OrderId(String value) {
    public OrderId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("orderId is required");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("orderId is required");
    }
    @Override public String toString() { return value; }
}