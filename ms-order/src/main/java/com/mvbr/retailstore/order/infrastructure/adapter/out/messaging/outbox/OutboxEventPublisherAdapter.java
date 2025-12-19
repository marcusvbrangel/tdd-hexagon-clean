package com.mvbr.retailstore.order.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.order.application.port.out.EventPublisher;
import com.mvbr.retailstore.order.domain.event.DomainEvent;
import com.mvbr.retailstore.order.domain.event.OrderCanceledEvent;
import com.mvbr.retailstore.order.domain.event.OrderConfirmedEvent;
import com.mvbr.retailstore.order.domain.event.OrderPlacedEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class OutboxEventPublisherAdapter implements EventPublisher {

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(DomainEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize event: " + event.eventType(), e);
        }

        outboxRepository.save(new OutboxMessageJpaEntity(
                event.eventId(),
                "Order",
                resolveAggregateId(event),
                event.eventType(),
                payload,
                event.occurredAt()
        ));
    }

    private String resolveAggregateId(DomainEvent event) {
        if (event instanceof OrderPlacedEvent orderPlacedEvent) {
            return orderPlacedEvent.orderId().value();
        }
        if (event instanceof OrderConfirmedEvent orderConfirmedEvent) {
            return orderConfirmedEvent.orderId().value();
        }
        if (event instanceof OrderCanceledEvent orderCanceledEvent) {
            return orderCanceledEvent.orderId().value();
        }
        throw new IllegalArgumentException("Unsupported event type for outbox: " + event.getClass().getName());
    }
}
