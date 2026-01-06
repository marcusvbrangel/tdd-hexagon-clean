package com.mvbr.retailstore.notification.application.command;

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
