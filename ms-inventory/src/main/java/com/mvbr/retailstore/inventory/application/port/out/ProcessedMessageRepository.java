package com.mvbr.retailstore.inventory.application.port.out;

import java.time.Instant;

public interface ProcessedMessageRepository {

    boolean markProcessedIfFirst(String messageId, String messageType, String aggregateId, Instant processedAt);
}
