package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging;

public final class TopicNames {

    private TopicNames() {}

    public static final String ORDER_EVENTS_V1 = "order.events.v1";
    public static final String INVENTORY_EVENTS_V1 = "inventory.events.v1";
    public static final String PAYMENT_EVENTS_V1 = "payment.events.v1";

    public static final String ORDER_COMMANDS_V1 = "order.commands.v1";
    public static final String INVENTORY_COMMANDS_V1 = "inventory.commands.v1";
    public static final String PAYMENT_COMMANDS_V1 = "payment.commands.v1";
}
