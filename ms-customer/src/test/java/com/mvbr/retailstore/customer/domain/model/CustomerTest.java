package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.event.CustomerCreatedEvent;
import com.mvbr.retailstore.customer.domain.event.DomainEvent;
import com.mvbr.retailstore.customer.domain.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerTest {

    private static final Instant NOW = Instant.parse("2025-01-01T10:00:00Z");

    @Test
    void createNewNormalizesValueObjectsAndEmitsCreatedEvent() {
        Customer customer = createCustomer(NOW);

        assertEquals(CustomerStatus.ACTIVE, customer.getCustomerStatus());
        assertEquals(NOW, customer.getCreatedAt());
        assertEquals(NOW, customer.getUpdatedAt());
        assertEquals(DocumentType.CPF, customer.getDocument().type());
        assertEquals("63977319957", customer.getDocument().value());
        assertEquals("joao@example.com", customer.getEmail().normalized());
        assertEquals("11987654321", customer.getPhone().toString());

        List<DomainEvent> events = customer.pullEvents();
        assertEquals(1, events.size());
        assertInstanceOf(CustomerCreatedEvent.class, events.get(0));
        CustomerCreatedEvent createdEvent = (CustomerCreatedEvent) events.get(0);
        assertEquals("customer.created", createdEvent.eventType());
        assertEquals(NOW, createdEvent.occurredAt());
        assertEquals(customer.getCustomerId(), createdEvent.customerId());
        assertFalse(createdEvent.eventId().isBlank());

        assertTrue(customer.pullEvents().isEmpty());
    }

    @Test
    void rejectInvalidDocumentOnCreateNew() {
        CustomerId customerId = new CustomerId("cust-2");
        assertThrows(DomainException.class, () -> Customer.createNew(
                customerId,
                "Joao",
                "Silva",
                DocumentType.CPF,
                "63977319958",
                new Email("joao@example.com"),
                new Phone("11987654321"),
                NOW
        ));
    }

    private static Customer createCustomer(Instant now) {
        CustomerId customerId = new CustomerId("cust-1");
        return Customer.createNew(
                customerId,
                "Joao",
                "Silva",
                DocumentType.CPF,
                "639.773.199-57",
                new Email("JOAO@EXAMPLE.COM"),
                new Phone("+55 (11) 9 8765-4321"),
                now
        );
    }
}
