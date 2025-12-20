package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.exception.DomainException;

// Value Object...
public record CustomerId(String value) {

    public CustomerId {
        if (value == null) {
            throw new DomainException("Customer ID cannot be null or blank");
        }
        value = value.trim();
        if (value.isBlank()) {
            throw new DomainException("Customer ID cannot be null or blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }

}
