package com.mvbr.retailstore.checkout.application.port.out;

public interface ProcessedEventRepository {
    boolean markProcessedIfFirst(String eventId, String eventType, String orderId);
}
