package com.mvbr.retailstore.notification.infrastructure.observability;

public final class MdcKeys {

    public static final String TRACE_ID = "trace_id";
    public static final String SPAN_ID = "span_id";

    public static final String CORRELATION_ID = "correlation_id";
    public static final String PARENT_CORRELATION_ID = "parent_correlation_id";

    public static final String ORDER_ID = "order_id";
    public static final String AGGREGATE_ID = "aggregate_id";
    public static final String AGGREGATE_TYPE = "aggregate_type";

    public static final String SAGA_ID = "saga_id";
    public static final String SAGA_NAME = "saga_name";
    public static final String SAGA_STEP = "saga_step";

    public static final String COMMAND_ID = "command_id";
    public static final String COMMAND_TYPE = "command_type";
    public static final String EVENT_ID = "event_id";
    public static final String EVENT_TYPE = "event_type";

    public static final String PRODUCER = "producer";

    private MdcKeys() {
    }
}
