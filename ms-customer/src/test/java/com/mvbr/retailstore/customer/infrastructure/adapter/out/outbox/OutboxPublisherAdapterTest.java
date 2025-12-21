package com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.customer.domain.event.CustomerChangedEvent;
import com.mvbr.retailstore.customer.domain.event.CustomerCreatedEvent;
import com.mvbr.retailstore.customer.domain.event.DomainEvent;
import com.mvbr.retailstore.customer.domain.model.CustomerId;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.entity.OutboxMessageJpaEntity;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.outbox.repository.OutboxJpaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class OutboxPublisherAdapterTest {

    private static final Instant NOW = Instant.parse("2025-01-01T10:00:00Z");

    @Test
    void storeSavesOutboxMessageForCreatedEvent() throws Exception {
        OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        OutboxPublisherAdapter adapter = new OutboxPublisherAdapter(repository, objectMapper);

        CustomerId customerId = new CustomerId("cust-1");
        CustomerCreatedEvent event = new CustomerCreatedEvent("event-1", NOW, customerId);

        adapter.store(List.of(event));

        ArgumentCaptor<OutboxMessageJpaEntity> captor = ArgumentCaptor.forClass(OutboxMessageJpaEntity.class);
        verify(repository).save(captor.capture());
        OutboxMessageJpaEntity message = captor.getValue();

        assertEquals("event-1", message.getEventId());
        assertEquals("Customer", message.getAggregateType());
        assertEquals("cust-1", message.getAggregateId());
        assertEquals("CustomerCreated", message.getEventType());
        assertEquals("customer.created", message.getTopic());
        assertEquals(NOW, message.getOccurredAt());
        assertNotNull(message.getPayloadJson());
        assertNotNull(message.getHeadersJson());

        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertEquals("event-1", payload.get("eventId").asText());
        assertEquals(NOW.toString(), payload.get("occurredAt").asText());
        assertEquals("cust-1", payload.get("customerId").asText());

        JsonNode headers = objectMapper.readTree(message.getHeadersJson());
        assertEquals("event-1", headers.get("eventId").asText());
        assertEquals("CustomerCreated", headers.get("eventType").asText());
        assertEquals("1", headers.get("schemaVersion").asText());
        assertEquals("customer-service", headers.get("producer").asText());
        assertEquals(NOW.toString(), headers.get("occurredAt").asText());
        assertEquals("event-1", headers.get("correlationId").asText());
        assertEquals("event-1", headers.get("causationId").asText());
        assertEquals("application/json", headers.get("contentType").asText());
    }

    @Test
    void storeSavesOutboxMessageForChangedEvent() {
        OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
        OutboxPublisherAdapter adapter = new OutboxPublisherAdapter(repository, new ObjectMapper());

        CustomerId customerId = new CustomerId("cust-2");
        CustomerChangedEvent event = new CustomerChangedEvent("event-2", NOW, customerId);

        adapter.store(List.of(event));

        ArgumentCaptor<OutboxMessageJpaEntity> captor = ArgumentCaptor.forClass(OutboxMessageJpaEntity.class);
        verify(repository).save(captor.capture());
        OutboxMessageJpaEntity message = captor.getValue();

        assertEquals("event-2", message.getEventId());
        assertEquals("Customer", message.getAggregateType());
        assertEquals("cust-2", message.getAggregateId());
        assertEquals("CustomerChanged", message.getEventType());
        assertEquals("customer.changed", message.getTopic());
        assertEquals(NOW, message.getOccurredAt());
    }

    @Test
    void storeIgnoresEmptyList() {
        OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
        OutboxPublisherAdapter adapter = new OutboxPublisherAdapter(repository, new ObjectMapper());

        adapter.store(List.of());

        verifyNoInteractions(repository);
    }

    @Test
    void storeRejectsNullEvent() {
        OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
        OutboxPublisherAdapter adapter = new OutboxPublisherAdapter(repository, new ObjectMapper());

        List<DomainEvent> events = new java.util.ArrayList<>();
        events.add(null);

        assertThrows(IllegalArgumentException.class, () -> adapter.store(events));
    }
}
