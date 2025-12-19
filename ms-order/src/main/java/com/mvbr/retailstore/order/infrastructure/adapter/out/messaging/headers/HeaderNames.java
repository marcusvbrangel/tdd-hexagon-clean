package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers;

public final class HeaderNames {

    public static final String EVENT_ID = "eventId";
    public static final String EVENT_TYPE = "eventType";
    public static final String SCHEMA_VERSION = "schemaVersion";
    public static final String PRODUCER = "producer";
    public static final String OCCURRED_AT = "occurredAt";
    public static final String CORRELATION_ID = "correlationId";
    public static final String CAUSATION_ID = "causationId";
    public static final String TRACEPARENT = "traceparent";
    public static final String CONTENT_TYPE = "contentType";

    private HeaderNames() {
    }
}
