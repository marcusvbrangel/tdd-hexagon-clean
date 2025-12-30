package com.mvbr.retailstore.inventory.domain.model;

/**
 * Identificador imutavel de produto no dominio de inventory.
 */
public record ProductId(String value) {
    public ProductId {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("productId is required");
        value = value.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("productId is required");
    }
    @Override public String toString() { return value; }
}