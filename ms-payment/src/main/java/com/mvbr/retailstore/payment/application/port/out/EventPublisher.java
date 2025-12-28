package com.mvbr.retailstore.payment.application.port.out;

import java.time.Instant;
import java.util.Map;

/**
 * Porta de publicacao de eventos (outbox).
 */
public interface EventPublisher {

    void publish(String topic,
                 String aggregateType,
                 String aggregateId,
                 String eventType,
                 Object payload,
                 Map<String, String> headers,
                 Instant occurredAt);
}
