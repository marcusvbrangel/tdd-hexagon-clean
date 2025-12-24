package com.mvbr.retailstore.checkout.domain.model;

/**
 * Etapas detalhadas da saga, usadas para controlar transicoes e timeouts.
 */
public enum SagaStep {
    STARTED,
    WAIT_INVENTORY,
    WAIT_PAYMENT,
    WAIT_ORDER_COMPLETION,
    COMPENSATING,
    DONE;

    /**
     * Converte valores persistidos (inclusive nomes antigos) para a etapa atual.
     * Usado pelos adaptadores JPA ao reidratar a saga.
     */
    public static SagaStep fromPersistence(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("step cannot be null/blank");
        }
        return switch (value) {
            case "INVENTORY_RESERVE_PENDING" -> WAIT_INVENTORY;
            case "PAYMENT_AUTHORIZE_PENDING" -> WAIT_PAYMENT;
            case "ORDER_COMPLETE_PENDING" -> WAIT_ORDER_COMPLETION;
            case "COMPENSATE_INVENTORY_RELEASE_PENDING",
                 "COMPENSATE_ORDER_CANCEL_PENDING",
                 "WAITING_COMPENSATIONS" -> COMPENSATING;
            default -> SagaStep.valueOf(value);
        };
    }
}
