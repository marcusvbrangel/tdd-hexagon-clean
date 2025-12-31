package com.mvbr.retailstore.customer.infrastructure.observability;

import com.mvbr.retailstore.customer.infrastructure.adapter.out.saga.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

public class KafkaRecordMdcInterceptor<K, V> implements RecordInterceptor<K, V> {

    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record,
                                               Consumer<K, V> consumer) {
        try {
            putFromHeader(record, HeaderNames.CORRELATION_ID, MdcKeys.CORRELATION_ID);
            putFromHeader(record, HeaderNames.CAUSATION_ID, MdcKeys.PARENT_CORRELATION_ID);

            putFromHeader(record, HeaderNames.EVENT_ID, MdcKeys.EVENT_ID);
            putFromHeader(record, HeaderNames.EVENT_TYPE, MdcKeys.EVENT_TYPE);
            putFromHeader(record, HeaderNames.PRODUCER, MdcKeys.PRODUCER);
        } catch (Exception ignored) {
            return record;
        }
        return record;
    }

    @Override
    public void afterRecord(ConsumerRecord<K, V> record,
                        Consumer<K, V> consumer) {
        MDC.clear();
    }

    private void putFromHeader(ConsumerRecord<K, V> record, String headerName, String mdcKey) {
        Header header = record.headers().lastHeader(headerName);
        if (header == null) {
            return;
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        if (value == null || value.isBlank()) {
            return;
        }
        MDC.put(mdcKey, value);
        MDC.put(headerName, value);
    }
}
