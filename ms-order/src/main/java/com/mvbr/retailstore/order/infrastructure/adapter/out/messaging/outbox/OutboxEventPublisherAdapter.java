package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.order.application.port.out.EventPublisher;
import com.mvbr.retailstore.order.domain.event.DomainEvent;
import com.mvbr.retailstore.order.domain.event.OrderCanceledEvent;
import com.mvbr.retailstore.order.domain.event.OrderConfirmedEvent;
import com.mvbr.retailstore.order.domain.event.OrderPlacedEvent;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderCanceledEventV1;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderConfirmedEventV1;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper.OrderCanceledEventMapper;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper.OrderConfirmedEventMapper;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper.OrderPlacedEventMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Map;

@Primary
@Component
public class OutboxEventPublisherAdapter implements EventPublisher {

    private static final String ORDER_AGGREGATE_TYPE = "Order";
    private static final String ORDER_PLACED_TOPIC = "order.placed";
    private static final String ORDER_CONFIRMED_TOPIC = "order.confirmed";
    private static final String ORDER_CANCELED_TOPIC = "order.canceled";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        OutboxMessageJpaEntity message = toOutboxMessage(event);
        outboxRepository.save(message);
    }

    private OutboxMessageJpaEntity toOutboxMessage(DomainEvent event) {
        if (event instanceof OrderPlacedEvent orderPlacedEvent) {
            return toOrderPlacedOutbox(orderPlacedEvent);
        }
        if (event instanceof OrderConfirmedEvent orderConfirmedEvent) {
            return toOrderConfirmedOutbox(orderConfirmedEvent);
        }
        if (event instanceof OrderCanceledEvent orderCanceledEvent) {
            return toOrderCanceledOutbox(orderCanceledEvent);
        }

        throw new IllegalArgumentException("Unsupported event type for outbox: " + event.getClass().getName());
    }

    private OutboxMessageJpaEntity toOrderPlacedOutbox(OrderPlacedEvent event) {
        OrderPlacedEventV1 dto = OrderPlacedEventMapper.toDto(event);
        return buildOutboxMessage(dto.eventId(), dto.orderId(), resolveLogicalEventType(event), ORDER_PLACED_TOPIC,
                writeValueAsString(dto), event.occurredAt());
    }

    private OutboxMessageJpaEntity toOrderConfirmedOutbox(OrderConfirmedEvent event) {
        OrderConfirmedEventV1 dto = OrderConfirmedEventMapper.toDto(event);
        return buildOutboxMessage(dto.eventId(), dto.orderId(), resolveLogicalEventType(event), ORDER_CONFIRMED_TOPIC,
                writeValueAsString(dto), event.occurredAt());
    }

    private OutboxMessageJpaEntity toOrderCanceledOutbox(OrderCanceledEvent event) {
        OrderCanceledEventV1 dto = OrderCanceledEventMapper.toDto(event);
        return buildOutboxMessage(dto.eventId(), dto.orderId(), resolveLogicalEventType(event), ORDER_CANCELED_TOPIC,
                writeValueAsString(dto), event.occurredAt());
    }

    private OutboxMessageJpaEntity buildOutboxMessage(String eventId,
                                                      String aggregateId,
                                                      String logicalEventType,
                                                      String topic,
                                                      String payloadJson,
                                                      java.time.Instant occurredAt) {
        String headersJson = writeValueAsString(buildHeaders(eventId, logicalEventType, occurredAt.toString()));

        return new OutboxMessageJpaEntity(
                eventId,
                ORDER_AGGREGATE_TYPE,
                aggregateId,
                logicalEventType,
                topic,
                payloadJson,
                headersJson,
                occurredAt
        );
    }

    private Map<String, String> buildHeaders(String eventId, String eventType, String occurredAt) {
        return SagaHeaders.build(eventId, eventType, occurredAt);
    }

    private String writeValueAsString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize value to JSON", e);
        }
    }

    private String resolveLogicalEventType(DomainEvent event) {
        String simpleName = event.getClass().getSimpleName();
        if (simpleName.endsWith("Event")) {
            return simpleName.substring(0, simpleName.length() - "Event".length());
        }
        return simpleName;
    }
}
