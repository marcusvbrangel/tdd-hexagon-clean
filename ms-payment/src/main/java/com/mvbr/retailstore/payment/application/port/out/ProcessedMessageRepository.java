package com.mvbr.retailstore.payment.application.port.out;

import java.time.Instant;

/**
 * Porta de idempotencia para comandos recebidos.
 */
public interface ProcessedMessageRepository {

    boolean markProcessedIfFirst(String messageId, String messageType, String aggregateId, Instant processedAt);
}
