package com.mvbr.retailstore.checkout.domain.model;

public enum SagaStatus {
    RUNNING,
    COMPLETED,
    CANCELED;

    public static SagaStatus fromPersistence(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status cannot be null/blank");
        }
        if ("CANCELLED".equalsIgnoreCase(value)) {
            return CANCELED;
        }
        return SagaStatus.valueOf(value);
    }
}
