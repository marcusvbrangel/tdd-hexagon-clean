package com.mvbr.retailstore.customer.application.service;

import com.mvbr.retailstore.customer.application.command.CreateCustomerCommand;
import com.mvbr.retailstore.customer.application.port.out.CustomerIdGenerator;
import com.mvbr.retailstore.customer.application.port.out.CustomerRepository;
import com.mvbr.retailstore.customer.application.port.out.OutboxPublisher;
import com.mvbr.retailstore.customer.domain.event.CustomerCreatedEvent;
import com.mvbr.retailstore.customer.domain.event.DomainEvent;
import com.mvbr.retailstore.customer.domain.exception.DomainException;
import com.mvbr.retailstore.customer.domain.exception.InvalidCustomerException;
import com.mvbr.retailstore.customer.domain.model.Customer;
import com.mvbr.retailstore.customer.domain.model.CustomerId;
import com.mvbr.retailstore.customer.domain.model.CustomerStatus;
import com.mvbr.retailstore.customer.domain.model.DocumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerCommandServiceTest {

    @Test
    void createPersistsCustomerAndStoresOutboxEvent() {
        CustomerRepository repository = org.mockito.Mockito.mock(CustomerRepository.class);
        CustomerIdGenerator idGenerator = org.mockito.Mockito.mock(CustomerIdGenerator.class);
        OutboxPublisher outboxPublisher = org.mockito.Mockito.mock(OutboxPublisher.class);

        CustomerId customerId = new CustomerId("cust-1");
        when(idGenerator.nextId()).thenReturn(customerId);
        when(repository.save(any(Customer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerCommandService service = new CustomerCommandService(repository, idGenerator, outboxPublisher);
        CreateCustomerCommand command = new CreateCustomerCommand(
                " Joao ",
                " Silva ",
                "cpf",
                "639.773.199-57",
                "JOAO@EXAMPLE.COM",
                "+55 (11) 9 8765-4321"
        );

        CustomerId result = service.create(command);

        assertEquals(customerId, result);

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        ArgumentCaptor<List> eventsCaptor = ArgumentCaptor.forClass(List.class);
        InOrder inOrder = inOrder(repository, outboxPublisher);
        inOrder.verify(repository).save(customerCaptor.capture());
        inOrder.verify(outboxPublisher).store(eventsCaptor.capture());

        Customer saved = customerCaptor.getValue();
        assertEquals(customerId, saved.getCustomerId());
        assertEquals("Joao", saved.getFirstName());
        assertEquals("Silva", saved.getLastName());
        assertEquals(DocumentType.CPF, saved.getDocument().type());
        assertEquals("63977319957", saved.getDocument().value());
        assertEquals("joao@example.com", saved.getEmail().normalized());
        assertEquals("11987654321", saved.getPhone().toString());
        assertEquals(CustomerStatus.ACTIVE, saved.getCustomerStatus());
        assertEquals(saved.getCreatedAt(), saved.getUpdatedAt());

        List<DomainEvent> events = eventsCaptor.getValue();
        assertEquals(1, events.size());
        assertInstanceOf(CustomerCreatedEvent.class, events.get(0));
        CustomerCreatedEvent event = (CustomerCreatedEvent) events.get(0);
        assertEquals("customer.created", event.eventType());
        assertEquals(customerId, event.customerId());
        assertEquals(saved.getCreatedAt(), event.occurredAt());
        assertNotNull(event.eventId());
        assertTrue(!event.eventId().isBlank());
    }

    @Test
    void createRejectsInvalidDocumentType() {
        CustomerRepository repository = org.mockito.Mockito.mock(CustomerRepository.class);
        CustomerIdGenerator idGenerator = org.mockito.Mockito.mock(CustomerIdGenerator.class);
        OutboxPublisher outboxPublisher = org.mockito.Mockito.mock(OutboxPublisher.class);

        when(idGenerator.nextId()).thenReturn(new CustomerId("cust-2"));

        CustomerCommandService service = new CustomerCommandService(repository, idGenerator, outboxPublisher);
        CreateCustomerCommand command = new CreateCustomerCommand(
                "Joao",
                "Silva",
                "invalid",
                "639.773.199-57",
                "joao@example.com",
                "11987654321"
        );

        assertThrows(InvalidCustomerException.class, () -> service.create(command));

        verify(repository, never()).save(any(Customer.class));
        verify(outboxPublisher, never()).store(anyList());
    }

    @Test
    void createRejectsBlankDocumentType() {
        CustomerRepository repository = org.mockito.Mockito.mock(CustomerRepository.class);
        CustomerIdGenerator idGenerator = org.mockito.Mockito.mock(CustomerIdGenerator.class);
        OutboxPublisher outboxPublisher = org.mockito.Mockito.mock(OutboxPublisher.class);

        when(idGenerator.nextId()).thenReturn(new CustomerId("cust-3"));

        CustomerCommandService service = new CustomerCommandService(repository, idGenerator, outboxPublisher);
        CreateCustomerCommand command = new CreateCustomerCommand(
                "Joao",
                "Silva",
                "  ",
                "639.773.199-57",
                "joao@example.com",
                "11987654321"
        );

        assertThrows(InvalidCustomerException.class, () -> service.create(command));

        verify(repository, never()).save(any(Customer.class));
        verify(outboxPublisher, never()).store(anyList());
    }

    @Test
    void createRejectsInvalidEmailOrPhone() {
        CustomerRepository repository = org.mockito.Mockito.mock(CustomerRepository.class);
        CustomerIdGenerator idGenerator = org.mockito.Mockito.mock(CustomerIdGenerator.class);
        OutboxPublisher outboxPublisher = org.mockito.Mockito.mock(OutboxPublisher.class);

        when(idGenerator.nextId()).thenReturn(new CustomerId("cust-4"));

        CustomerCommandService service = new CustomerCommandService(repository, idGenerator, outboxPublisher);
        CreateCustomerCommand command = new CreateCustomerCommand(
                "Joao",
                "Silva",
                "cpf",
                "639.773.199-57",
                "not-an-email",
                "123"
        );

        assertThrows(DomainException.class, () -> service.create(command));

        verify(repository, never()).save(any(Customer.class));
        verify(outboxPublisher, never()).store(anyList());
    }
}
