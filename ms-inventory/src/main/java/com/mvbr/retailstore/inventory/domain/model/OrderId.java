package com.mvbr.retailstore.inventory.domain.model;

public record OrderId(String value) {

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
