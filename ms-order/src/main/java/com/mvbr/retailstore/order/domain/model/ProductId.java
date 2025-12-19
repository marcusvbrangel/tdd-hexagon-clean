package com.mvbr.retailstore.order.domain.model;

import com.mvbr.retailstore.order.domain.exception.InvalidOrderException;

public record ProductId(String value) {

    public ProductId {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("Product ID cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
