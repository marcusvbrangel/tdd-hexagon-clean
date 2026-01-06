package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.mapper;

import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.NotificationSendCommandV1;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NotificationCommands {

    private static final List<String> EMAIL = List.of("EMAIL");

    private NotificationCommands() {
    }

    public static NotificationSendCommandV1 orderReceived(String commandId,
                                                          String occurredAt,
                                                          String orderId,
                                                          String customerId,
                                                          int itemsCount,
                                                          String total,
                                                          String currency) {
        Map<String, Object> data = baseData(orderId, customerId, itemsCount, total, currency);
        return build(commandId, occurredAt, orderId, customerId, "ORDER_RECEIVED", data);
    }

    public static NotificationSendCommandV1 inventoryReserved(String commandId,
                                                              String occurredAt,
                                                              String orderId,
                                                              String customerId,
                                                              int itemsCount,
                                                              String total,
                                                              String currency) {
        Map<String, Object> data = baseData(orderId, customerId, itemsCount, total, currency);
        return build(commandId, occurredAt, orderId, customerId, "INVENTORY_RESERVED", data);
    }

    public static NotificationSendCommandV1 inventoryRejected(String commandId,
                                                              String occurredAt,
                                                              String orderId,
                                                              String customerId,
                                                              int itemsCount,
                                                              String total,
                                                              String currency,
                                                              String reason) {
        Map<String, Object> data = baseData(orderId, customerId, itemsCount, total, currency);
        putIfPresent(data, "reason", reason);
        return build(commandId, occurredAt, orderId, customerId, "INVENTORY_REJECTED", data);
    }

    public static NotificationSendCommandV1 paymentAuthorized(String commandId,
                                                              String occurredAt,
                                                              String orderId,
                                                              String customerId,
                                                              int itemsCount,
                                                              String total,
                                                              String currency) {
        Map<String, Object> data = baseData(orderId, customerId, itemsCount, total, currency);
        return build(commandId, occurredAt, orderId, customerId, "PAYMENT_AUTHORIZED", data);
    }

    public static NotificationSendCommandV1 paymentDeclined(String commandId,
                                                            String occurredAt,
                                                            String orderId,
                                                            String customerId,
                                                            int itemsCount,
                                                            String total,
                                                            String currency,
                                                            String reason) {
        Map<String, Object> data = baseData(orderId, customerId, itemsCount, total, currency);
        putIfPresent(data, "reason", reason);
        return build(commandId, occurredAt, orderId, customerId, "PAYMENT_DECLINED", data);
    }

    public static NotificationSendCommandV1 orderCanceled(String commandId,
                                                          String occurredAt,
                                                          String orderId,
                                                          String customerId,
                                                          int itemsCount,
                                                          String total,
                                                          String currency,
                                                          String reason) {
        Map<String, Object> data = baseData(orderId, customerId, itemsCount, total, currency);
        putIfPresent(data, "reason", reason);
        return build(commandId, occurredAt, orderId, customerId, "ORDER_CANCELED", data);
    }

    private static NotificationSendCommandV1 build(String commandId,
                                                   String occurredAt,
                                                   String orderId,
                                                   String customerId,
                                                   String template,
                                                   Map<String, Object> data) {
        return new NotificationSendCommandV1(
                commandId,
                occurredAt,
                orderId,
                customerId,
                template,
                EMAIL,
                data
        );
    }

    private static Map<String, Object> baseData(String orderId,
                                                String customerId,
                                                int itemsCount,
                                                String total,
                                                String currency) {
        Map<String, Object> data = new LinkedHashMap<>();
        putIfPresent(data, "orderId", orderId);
        putIfPresent(data, "customerId", customerId);
        data.put("itemsCount", itemsCount);
        putIfPresent(data, "total", total);
        putIfPresent(data, "currency", currency);
        return data;
    }

    private static void putIfPresent(Map<String, Object> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }
}
