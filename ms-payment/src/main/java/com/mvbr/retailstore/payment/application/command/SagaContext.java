package com.mvbr.retailstore.payment.application.command;

/**
 * Contexto de saga carregado a partir dos headers Kafka.
 */
public record SagaContext(
        String sagaId,
        String correlationId,
        String causationId,
        String sagaName,
        String sagaStep,
        String aggregateType,
        String aggregateId
) {
}
