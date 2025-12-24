package com.mvbr.retailstore.checkout.application.port.out;

import java.util.Map;

/**
 * Porta de saida para publicar comandos (com suporte a headers).
 * Implementada por OutboxCommandPublisherAdapter.
 */
public interface CommandPublisher {
    /**
     * Publica um comando com payload e headers para o topico indicado.
     */
    void publish(String topic, String key, String commandType, Object payload, Map<String, String> headers);
}
