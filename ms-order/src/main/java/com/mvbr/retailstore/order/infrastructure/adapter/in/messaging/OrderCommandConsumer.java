package com.mvbr.retailstore.order.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.order.application.port.in.CancelOrderUseCase;
import com.mvbr.retailstore.order.application.port.in.CompleteOrderUseCase;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderCancelCommandV1;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderCompleteCommandV1;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Consumidor de comandos de order vindos do orquestrador (order.complete / order.cancel).
 */
@Component
public class OrderCommandConsumer {

    private static final Logger log = Logger.getLogger(OrderCommandConsumer.class.getName());

    private final ObjectMapper objectMapper;
    private final CompleteOrderUseCase completeOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;

    public OrderCommandConsumer(ObjectMapper objectMapper,
                                CompleteOrderUseCase completeOrderUseCase,
                                CancelOrderUseCase cancelOrderUseCase) {
        this.objectMapper = objectMapper;
        this.completeOrderUseCase = completeOrderUseCase;
        this.cancelOrderUseCase = cancelOrderUseCase;
    }

    @KafkaListener(
            topics = "order.commands.v1",
            groupId = "${spring.kafka.consumer.group-id:ms-order}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String commandType = header(record, HeaderNames.COMMAND_TYPE)
                .orElseGet(() -> header(record, HeaderNames.EVENT_TYPE).orElse(""));

        if (commandType == null || commandType.isBlank()) {
            log.warning("OrderCommandConsumer: missing command type headers. Ignoring message.");
            return;
        }

        withMdc(record, () -> {
            try {
                switch (commandType) {
                    case "order.complete" -> handleComplete(record.value());
                    case "order.cancel" -> handleCancel(record.value());
                    default -> log.warning("OrderCommandConsumer: unknown commandType=" + commandType);
                }
            } catch (Exception e) {
                log.severe("OrderCommandConsumer failed. commandType=" + commandType + " error=" + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }

    private void handleComplete(String payload) throws Exception {
        OrderCompleteCommandV1 dto = objectMapper.readValue(payload, OrderCompleteCommandV1.class);
        completeOrderUseCase.complete(dto.orderId());
    }

    private void handleCancel(String payload) throws Exception {
        OrderCancelCommandV1 dto = objectMapper.readValue(payload, OrderCancelCommandV1.class);
        cancelOrderUseCase.cancel(dto.orderId());
    }

    private Optional<String> header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        if (header == null) {
            return Optional.empty();
        }
        String value = new String(header.value(), StandardCharsets.UTF_8);
        return (value == null || value.isBlank()) ? Optional.empty() : Optional.of(value);
    }

    private void withMdc(ConsumerRecord<String, String> record, Runnable action) {
        Map<String, String> previous = new HashMap<>();
        applyMdc(record, previous, HeaderNames.CORRELATION_ID);
        applyMdc(record, previous, HeaderNames.CAUSATION_ID);
        applyMdc(record, previous, HeaderNames.SAGA_ID);
        applyMdc(record, previous, HeaderNames.SAGA_NAME);
        applyMdc(record, previous, HeaderNames.SAGA_STEP);
        applyMdc(record, previous, HeaderNames.TRACEPARENT);
        try {
            action.run();
        } finally {
            restoreMdc(previous);
        }
    }

    private void applyMdc(ConsumerRecord<String, String> record,
                          Map<String, String> previous,
                          String headerName) {
        previous.put(headerName, MDC.get(headerName));
        header(record, headerName)
                .ifPresentOrElse(value -> MDC.put(headerName, value), () -> MDC.remove(headerName));
    }

    private void restoreMdc(Map<String, String> previous) {
        for (Map.Entry<String, String> entry : previous.entrySet()) {
            if (entry.getValue() == null) {
                MDC.remove(entry.getKey());
            } else {
                MDC.put(entry.getKey(), entry.getValue());
            }
        }
    }
}
