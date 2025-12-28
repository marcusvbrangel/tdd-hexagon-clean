package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers;

public final class HeaderNames {

    private HeaderNames() { }

    // ============================
    // Standard / Observability
    // ============================
    public static final String TRACEPARENT = "traceparent";
    public static final String CONTENT_TYPE = "content-type";

    // ============================
    // Event envelope (custom)
    // ============================
    public static final String EVENT_ID = "x-event-id";
    public static final String EVENT_TYPE = "x-event-type";
    public static final String COMMAND_ID = "x-command-id";
    public static final String COMMAND_TYPE = "x-command-type";
    public static final String OCCURRED_AT = "x-occurred-at";

    public static final String SCHEMA_VERSION = "x-schema-version";     // ex: "1"
    public static final String TOPIC_VERSION = "x-topic-version";       // ex: "v1"
    public static final String PRODUCER = "x-producer";                 // ex: "ms-order"

    public static final String AGGREGATE_TYPE = "x-aggregate-type";     // ex: "Order"
    public static final String AGGREGATE_ID = "x-aggregate-id";         // ex: "<orderId>"

    public static final String CORRELATION_ID = "x-correlation-id";
    public static final String CAUSATION_ID = "x-causation-id";

    // ============================
    // (Futuro) Saga headers (se você quiser)
    // ============================
    public static final String SAGA_ID = "x-saga-id";
    public static final String SAGA_NAME = "x-saga-name";
    public static final String SAGA_STEP = "x-saga-step";
}
