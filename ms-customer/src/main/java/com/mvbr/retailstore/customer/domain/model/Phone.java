package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.exception.DomainException;

// Value Object...
public record Phone(String value) {

    public Phone {
        if (value == null || value.isBlank()) {
            throw new DomainException("Phone is required");
        }
    }

    @Override
    public String toString() {
        return value;
    }

}
