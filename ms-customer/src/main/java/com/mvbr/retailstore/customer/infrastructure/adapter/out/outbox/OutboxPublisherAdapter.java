package com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.customer.application.port.out.OutboxPublisher;
import com.mvbr.retailstore.customer.domain.event.CustomerChangedEvent;
import com.mvbr.retailstore.customer.domain.event.CustomerCreatedEvent;
import com.mvbr.retailstore.customer.domain.event.DomainEvent;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.dto.CustomerChangedEventV1;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.dto.CustomerCreatedEventV1;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.saga.SagaHeaders;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.mapper.CustomerChangedEventMapper;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.mapper.CustomerCreatedEventMapper;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.entity.OutboxMessageJpaEntity;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.repository.OutboxJpaRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Primary
@Component
public class OutboxPublisherAdapter implements OutboxPublisher {

    private static final String CUSTOMER_AGGREGATE_TYPE = "Customer";
    private static final String CUSTOMER_CREATED_TOPIC = "customer.created";
    private static final String CUSTOMER_CHANGED_TOPIC = "customer.changed";

    private final OutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxPublisherAdapter(OutboxJpaRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void store(List<DomainEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }

        for (DomainEvent event : events) {
            if (event == null) {
                throw new IllegalArgumentException("Event cannot be null");
            }
            outboxRepository.save(toOutboxMessage(event));
        }
    }

    private OutboxMessageJpaEntity toOutboxMessage(DomainEvent event) {
        if (event instanceof CustomerCreatedEvent createdEvent) {
            return toCustomerCreatedOutbox(createdEvent);
        }
        if (event instanceof CustomerChangedEvent changedEvent) {
            return toCustomerChangedOutbox(changedEvent);
        }
        throw new IllegalArgumentException("Unsupported event type for outbox: " + event.getClass().getName());
    }

    private OutboxMessageJpaEntity toCustomerCreatedOutbox(CustomerCreatedEvent event) {
        CustomerCreatedEventV1 dto = CustomerCreatedEventMapper.toDto(event);
        return buildOutboxMessage(
                dto.eventId(),
                dto.customerId(),
                resolveLogicalEventType(event),
                CUSTOMER_CREATED_TOPIC,
                writeValueAsString(dto),
                event.occurredAt()
        );
    }

    private OutboxMessageJpaEntity toCustomerChangedOutbox(CustomerChangedEvent event) {
        CustomerChangedEventV1 dto = CustomerChangedEventMapper.toDto(event);
        return buildOutboxMessage(
                dto.eventId(),
                dto.customerId(),
                resolveLogicalEventType(event),
                CUSTOMER_CHANGED_TOPIC,
                writeValueAsString(dto),
                event.occurredAt()
        );
    }

    private OutboxMessageJpaEntity buildOutboxMessage(String eventId,
                                                      String aggregateId,
                                                      String logicalEventType,
                                                      String topic,
                                                      String payloadJson,
                                                      Instant occurredAt) {
        String headersJson = writeValueAsString(buildHeaders(eventId, logicalEventType, occurredAt.toString()));
        return new OutboxMessageJpaEntity(
                eventId,
                CUSTOMER_AGGREGATE_TYPE,
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
