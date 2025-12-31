package com.mvbr.retailstore.payment.infrastructure.observability;

import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class KafkaCorrelationProducerInterceptor implements ProducerInterceptor<Object, Object> {

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        putIfAbsent(record, HeaderNames.CORRELATION_ID, resolveValue(MdcKeys.CORRELATION_ID, HeaderNames.CORRELATION_ID));
        putIfAbsent(record, HeaderNames.CAUSATION_ID, resolveValue(MdcKeys.PARENT_CORRELATION_ID, HeaderNames.CAUSATION_ID));

        putIfAbsent(record, HeaderNames.AGGREGATE_ID, resolveValue(MdcKeys.AGGREGATE_ID, HeaderNames.AGGREGATE_ID));
        putIfAbsent(record, HeaderNames.AGGREGATE_TYPE, resolveValue(MdcKeys.AGGREGATE_TYPE, HeaderNames.AGGREGATE_TYPE));

        putIfAbsent(record, HeaderNames.SAGA_ID, resolveValue(MdcKeys.SAGA_ID, HeaderNames.SAGA_ID));
        putIfAbsent(record, HeaderNames.SAGA_NAME, resolveValue(MdcKeys.SAGA_NAME, HeaderNames.SAGA_NAME));
        putIfAbsent(record, HeaderNames.SAGA_STEP, resolveValue(MdcKeys.SAGA_STEP, HeaderNames.SAGA_STEP));

        putIfAbsent(record, HeaderNames.COMMAND_ID, resolveValue(MdcKeys.COMMAND_ID, HeaderNames.COMMAND_ID));
        putIfAbsent(record, HeaderNames.COMMAND_TYPE, resolveValue(MdcKeys.COMMAND_TYPE, HeaderNames.COMMAND_TYPE));
        putIfAbsent(record, HeaderNames.EVENT_ID, resolveValue(MdcKeys.EVENT_ID, HeaderNames.EVENT_ID));
        putIfAbsent(record, HeaderNames.EVENT_TYPE, resolveValue(MdcKeys.EVENT_TYPE, HeaderNames.EVENT_TYPE));

        putIfAbsent(record, HeaderNames.PRODUCER, resolveValue(MdcKeys.PRODUCER, HeaderNames.PRODUCER));

        return record;
    }

    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
    }

    @Override
    public void close() {
    }

    @Override
    public void configure(Map<String, ?> configs) {
    }

    private static String resolveValue(String mdcKey, String fallbackKey) {
        String value = MDC.get(mdcKey);
        if (value == null || value.isBlank()) {
            value = MDC.get(fallbackKey);
        }
        return value;
    }

    private static void putIfAbsent(ProducerRecord<Object, Object> record, String headerName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (record.headers().lastHeader(headerName) != null) {
            return;
        }
        record.headers().add(new RecordHeader(headerName, value.getBytes(StandardCharsets.UTF_8)));
    }
}
