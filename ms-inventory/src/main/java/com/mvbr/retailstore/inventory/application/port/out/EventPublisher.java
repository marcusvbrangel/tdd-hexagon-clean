package com.mvbr.retailstore.inventory.application.port.out;

import java.time.Instant;
import java.util.Map;

/**
 * Porta de saida para publicacao de eventos (via outbox).
 */
public interface EventPublisher {

    /**
     * Persiste um evento para publicacao assicrona.
     */
    void publish(String topic,
                 String aggregateType,
                 String aggregateId,
                 String eventType,
                 Object payload,
                 Map<String, String> headers,
                 Instant occurredAt);
}
