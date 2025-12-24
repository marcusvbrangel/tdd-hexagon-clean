package com.mvbr.retailstore.checkout.domain.model;

/**
 * Estados de alto nivel da saga durante o fluxo de checkout.
 * Persistido como string e convertido ao carregar o agregado.
 */
public enum SagaStatus {
    RUNNING,
    COMPLETED,
    CANCELED;

    /**
     * Converte o valor persistido para o enum, aceitando alias legados.
     * Chamado pelos adaptadores JPA ao restaurar a saga.
     */
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
