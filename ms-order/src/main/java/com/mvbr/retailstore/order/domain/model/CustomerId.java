package com.mvbr.retailstore.order.domain.model;

import com.mvbr.retailstore.order.domain.exception.InvalidOrderException;

public record CustomerId(String value) {

    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new InvalidOrderException("Customer ID cannot be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
