package com.mvbr.estudo.tdd.domain.model;

import com.mvbr.estudo.tdd.domain.exception.InvalidOrderException;

public record OrderId(String value) {

    public OrderId {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("Order ID cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
