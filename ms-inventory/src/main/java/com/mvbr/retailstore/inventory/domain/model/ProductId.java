package com.mvbr.retailstore.inventory.domain.model;

public record ProductId(String value) {

    public ProductId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
