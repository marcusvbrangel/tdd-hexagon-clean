package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.order.application.port.out.EventPublisher;
import com.mvbr.retailstore.order.domain.event.DomainEvent;
import com.mvbr.retailstore.order.domain.event.OrderCanceledEvent;
import com.mvbr.retailstore.order.domain.event.OrderCompletedEvent;
import com.mvbr.retailstore.order.domain.event.OrderConfirmedEvent;
import com.mvbr.retailstore.order.domain.event.OrderPlacedEvent;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderCanceledEventV1;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderCompletedEventV1;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderConfirmedEventV1;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.dto.OrderPlacedEventV1;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.headers.SagaHeaders;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper.OrderCanceledEventMapper;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper.OrderCompletedEventMapper;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper.OrderConfirmedEventMapper;
import com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.mapper.OrderPlacedEventMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Primary
@Component
public class OutboxEventPublisherAdapter implements EventPublisher {

    private static final String ORDER_AGGREGATE_TYPE = "Order";

    // Canal (domínio + categoria + versão)
    private static final String ORDER_EVENTS_TOPIC = "order.events.v1";

    // Versão “lógica” do canal (vai em headers x-topic-version)
    private static final String TOPIC_VERSION = "v1";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        outboxRepository.save(toOutboxMessage(event));
    }

    private OutboxMessageJpaEntity toOutboxMessage(DomainEvent event) {

        if (event instanceof OrderPlacedEvent e) {
            return toOrderPlacedOutbox(e);
        }
        if (event instanceof OrderConfirmedEvent e) {
            return toOrderConfirmedOutbox(e);
        }
        if (event instanceof OrderCanceledEvent e) {
            return toOrderCanceledOutbox(e);
        }
        if (event instanceof OrderCompletedEvent e) {
            return toOrderCompletedOutbox(e);
        }

        throw new IllegalArgumentException("Unsupported event type for outbox: " + event.getClass().getName());
    }

    private OutboxMessageJpaEntity toOrderPlacedOutbox(OrderPlacedEvent event) {
        OrderPlacedEventV1 dto = OrderPlacedEventMapper.toDto(event);
        return buildOutboxMessage(
                dto.eventId(),
                dto.orderId(),
                event.eventType(),
                ORDER_EVENTS_TOPIC,
                writeValueAsString(dto),
                event.occurredAt()
        );
    }

    private OutboxMessageJpaEntity toOrderConfirmedOutbox(OrderConfirmedEvent event) {
        OrderConfirmedEventV1 dto = OrderConfirmedEventMapper.toDto(event);
        return buildOutboxMessage(
                dto.eventId(),
                dto.orderId(),
                event.eventType(),
                ORDER_EVENTS_TOPIC,
                writeValueAsString(dto),
                event.occurredAt()
        );
    }

    private OutboxMessageJpaEntity toOrderCanceledOutbox(OrderCanceledEvent event) {
        OrderCanceledEventV1 dto = OrderCanceledEventMapper.toDto(event);
        return buildOutboxMessage(
                dto.eventId(),
                dto.orderId(),
                event.eventType(),
                ORDER_EVENTS_TOPIC,
                writeValueAsString(dto),
                event.occurredAt()
        );
    }

    private OutboxMessageJpaEntity toOrderCompletedOutbox(OrderCompletedEvent event) {
        OrderCompletedEventV1 dto = OrderCompletedEventMapper.toDto(event);
        return buildOutboxMessage(
                dto.eventId(),
                dto.orderId(),
                event.eventType(),
                ORDER_EVENTS_TOPIC,
                writeValueAsString(dto),
                event.occurredAt()
        );
    }

    private OutboxMessageJpaEntity buildOutboxMessage(String eventId,
                                                      String aggregateId,
                                                      String eventType,
                                                      String topic,
                                                      String payloadJson,
                                                      Instant occurredAt) {

        String headersJson = writeValueAsString(
                SagaHeaders.build(
                        eventId,
                        eventType,
                        occurredAt.toString(),
                        ORDER_AGGREGATE_TYPE,
                        aggregateId,
                        TOPIC_VERSION
                )
        );

        return new OutboxMessageJpaEntity(
                eventId,
                ORDER_AGGREGATE_TYPE,
                aggregateId,
                eventType,        // ex: "order.placed"
                topic,            // ex: "order.events.v1"
                payloadJson,
                headersJson,
                occurredAt
        );
    }

    private String writeValueAsString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize value to JSON", e);
        }
    }
}
