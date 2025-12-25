package com.mvbr.retailstore.inventory.application.command;

/**
 * Contexto de saga carregado a partir dos headers Kafka.
 * Mantem rastreabilidade entre servicos.
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
