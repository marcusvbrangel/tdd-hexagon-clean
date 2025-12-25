package com.mvbr.retailstore.inventory.domain.model;

/**
 * Value object de quantidade. Mantem a regra basica de ser positiva.
 */
public record Quantity(long value) {

    /**
     * Valida a quantidade para manter invariantes do dominio.
     */
    public Quantity {
        if (value <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
    }
}
