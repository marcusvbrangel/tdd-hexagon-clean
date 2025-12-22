package com.mvbr.retailstore.order.domain.event;

public final class EventTypes {

    private EventTypes() { }

    public static final String ORDER_PLACED = "order.placed";
    public static final String ORDER_CONFIRMED = "order.confirmed";
    public static final String ORDER_CANCELED = "order.canceled";
    public static final String ORDER_COMPLETED = "order.completed";
}
