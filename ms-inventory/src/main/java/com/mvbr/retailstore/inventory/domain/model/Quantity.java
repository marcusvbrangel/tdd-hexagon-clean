package com.mvbr.retailstore.inventory.domain.model;

public record Quantity(long value) {

    public Quantity {
        if (value <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
    }
}
