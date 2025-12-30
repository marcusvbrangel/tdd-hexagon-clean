package com.mvbr.retailstore.inventory.domain.model;

/**
 * Identificador imutavel da reserva.
 */
public record ReservationId(String value) {

    public ReservationId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("reservationId is required");
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("reservationId is required");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
