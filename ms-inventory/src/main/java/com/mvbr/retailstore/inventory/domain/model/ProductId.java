package com.mvbr.retailstore.inventory.domain.model;

/**
 * Identificador imutavel de produto no dominio de inventory.
 * Garante que o valor nao e nulo/vazio no momento da criacao.
 */
public record ProductId(String value) {

    /**
     * Valida a entrada para evitar IDs invalidos no dominio.
     */
    public ProductId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("productId is required");
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
