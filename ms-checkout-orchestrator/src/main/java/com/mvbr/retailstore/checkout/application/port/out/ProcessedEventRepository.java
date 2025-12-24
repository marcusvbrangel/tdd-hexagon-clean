package com.mvbr.retailstore.checkout.application.port.out;

/**
 * Porta de saida para controle de idempotencia de eventos consumidos.
 */
public interface ProcessedEventRepository {
    /**
     * Marca o evento como processado se for a primeira ocorrencia.
     * Retorna false quando o evento ja foi visto.
     */
    boolean markProcessedIfFirst(String eventId, String eventType, String orderId);
}
