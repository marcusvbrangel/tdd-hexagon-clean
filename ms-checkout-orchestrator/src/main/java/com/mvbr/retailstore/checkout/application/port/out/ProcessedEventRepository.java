package com.mvbr.retailstore.checkout.application.port.out;

public interface ProcessedEventRepository {
    boolean alreadyProcessed(String eventId);

    void markProcessed(String eventId, String eventType, String orderId);
}
