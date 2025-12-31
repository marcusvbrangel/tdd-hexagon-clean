package com.mvbr.retailstore.checkout.infrastructure.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.MDC;

import java.util.function.Supplier;

public final class BusinessSpan {

    private static final Tracer TRACER =
            GlobalOpenTelemetry.getTracer("retail-store.checkout-orchestrator");

    private BusinessSpan() {
    }

    public static <T> T inSpan(String name, Supplier<T> supplier) {
        Span span = TRACER.spanBuilder(name)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            enrich(span);
            return supplier.get();
        } catch (Exception e) {
            span.recordException(e);
            span.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            span.end();
        }
    }

    public static void inSpan(String name, Runnable runnable) {
        inSpan(name, () -> {
            runnable.run();
            return null;
        });
    }

    private static void enrich(Span span) {
        set(span, "correlation.id", MDC.get(MdcKeys.CORRELATION_ID));
        set(span, "correlation.parent_id", MDC.get(MdcKeys.PARENT_CORRELATION_ID));
        set(span, "order.id", MDC.get(MdcKeys.ORDER_ID));
        set(span, "saga.id", MDC.get(MdcKeys.SAGA_ID));
        set(span, "saga.name", MDC.get(MdcKeys.SAGA_NAME));
        set(span, "saga.step", MDC.get(MdcKeys.SAGA_STEP));
        set(span, "command.type", MDC.get(MdcKeys.COMMAND_TYPE));
        set(span, "event.type", MDC.get(MdcKeys.EVENT_TYPE));
    }

    private static void set(Span span, String key, String value) {
        if (value != null && !value.isBlank()) {
            span.setAttribute(key, value);
        }
    }
}
