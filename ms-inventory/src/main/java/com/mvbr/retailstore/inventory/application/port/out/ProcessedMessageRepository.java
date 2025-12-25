package com.mvbr.retailstore.inventory.application.port.out;

import java.time.Instant;

/**
 * Porta de idempotencia para comandos recebidos.
 */
public interface ProcessedMessageRepository {

    /**
     * Registra um comando como processado; retorna false se ja existia.
     */
    boolean markProcessedIfFirst(String messageId, String messageType, String aggregateId, Instant processedAt);
}
