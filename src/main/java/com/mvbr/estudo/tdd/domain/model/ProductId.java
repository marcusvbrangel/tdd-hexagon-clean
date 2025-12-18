package com.mvbr.estudo.tdd.domain.model;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;

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
