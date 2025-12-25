package com.mvbr.retailstore.inventory.domain.model;

/**
 * Identificador imutavel do pedido associado a uma reserva de estoque.
 */
public record OrderId(String value) {

    /**
     * Valida a entrada para evitar IDs invalidos no dominio.
     */
    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
    }

    /**
     * String amigavel para logs e rastreio.
     */
    @Override
    public String toString() {
        return value;
    }
}
