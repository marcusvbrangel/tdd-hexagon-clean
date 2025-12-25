package com.mvbr.retailstore.inventory.application.port.out;

import java.time.Instant;
import java.util.Map;

public interface EventPublisher {

    void publish(String topic,
                 String aggregateType,
                 String aggregateId,
                 String eventType,
                 Object payload,
                 Map<String, String> headers,
                 Instant occurredAt);
}
