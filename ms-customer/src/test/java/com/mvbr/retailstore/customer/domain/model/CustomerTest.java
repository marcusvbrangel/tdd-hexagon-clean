package com.mvbr.retailstore.customer.domain.model;

import com.mvbr.retailstore.customer.domain.event.CustomerChangedEvent;
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
    void changeFirstNameUpdatesTimestampAndEmitsChangedEvent() {
        Customer customer = createCustomer(NOW);
        customer.pullEvents();

        Instant later = NOW.plusSeconds(10);
        customer.changeFirstName("Maria", later);

        assertEquals("Maria", customer.getFirstName());
        assertEquals(later, customer.getUpdatedAt());

        List<DomainEvent> events = customer.pullEvents();
        assertEquals(1, events.size());
        assertInstanceOf(CustomerChangedEvent.class, events.get(0));
        CustomerChangedEvent changedEvent = (CustomerChangedEvent) events.get(0);
        assertEquals("customer.changed", changedEvent.eventType());
        assertEquals(later, changedEvent.occurredAt());
        assertEquals(customer.getCustomerId(), changedEvent.customerId());
    }

    @Test
    void changeDocumentUpdatesTypeAndValueAndEmitsChangedEvent() {
        Customer customer = createCustomer(NOW);
        customer.pullEvents();

        Instant later = NOW.plusSeconds(20);
        customer.changeDocument(DocumentType.CNPJ, "90.383.305/2048-93", later);

        assertEquals(DocumentType.CNPJ, customer.getDocument().type());
        assertEquals("90383305204893", customer.getDocument().value());
        assertEquals(later, customer.getUpdatedAt());

        List<DomainEvent> events = customer.pullEvents();
        assertEquals(1, events.size());
        assertInstanceOf(CustomerChangedEvent.class, events.get(0));
    }

    @Test
    void noEventWhenValueDoesNotChange() {
        Customer customer = createCustomer(NOW);
        customer.pullEvents();

        Instant later = NOW.plusSeconds(5);
        customer.changeLastName(" Silva ", later);

        assertEquals("Silva", customer.getLastName());
        assertEquals(NOW, customer.getUpdatedAt());
        assertTrue(customer.pullEvents().isEmpty());
    }

    @Test
    void rejectPastTimestampOnChange() {
        Customer customer = createCustomer(NOW);
        customer.pullEvents();

        Instant past = NOW.minusSeconds(1);
        assertThrows(DomainException.class, () -> customer.changeFirstName("Ana", past));
        assertEquals("Joao", customer.getFirstName());
        assertEquals(NOW, customer.getUpdatedAt());
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
