package com.mvbr.retailstore.inventory.application.command;

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
