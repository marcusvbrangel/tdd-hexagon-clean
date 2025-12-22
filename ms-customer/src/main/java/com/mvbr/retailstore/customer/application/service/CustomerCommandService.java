package com.mvbr.retailstore.customer.application.service;

import com.mvbr.retailstore.customer.application.command.*;
import com.mvbr.retailstore.customer.application.port.in.*;
import com.mvbr.retailstore.customer.application.port.out.CustomerIdGenerator;
import com.mvbr.retailstore.customer.application.port.out.CustomerRepository;
import com.mvbr.retailstore.customer.application.port.out.OutboxPublisher;
import com.mvbr.retailstore.customer.domain.exception.InvalidCustomerException;
import com.mvbr.retailstore.customer.domain.model.*;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

public class CustomerCommandService implements
        CreateCustomerUseCase,
        UpdateCustomerUseCase,
        ActivateCustomerUseCase,
        DeactivateCustomerUseCase,
        BlockCustomerUseCase,
        GetCustomerUseCase {

    private final CustomerRepository customerRepository;
    private final CustomerIdGenerator customerIdGenerator;
    private final OutboxPublisher outboxPublisher;

    public CustomerCommandService(CustomerRepository customerRepository,
                                  CustomerIdGenerator customerIdGenerator,
                                  OutboxPublisher outboxPublisher) {
        this.customerRepository = customerRepository;
        this.customerIdGenerator = customerIdGenerator;
        this.outboxPublisher = outboxPublisher;
    }

    @Override
    @Transactional
    public CustomerId create(CreateCustomerCommand command) {

        // identity creation...
        CustomerId customerId = customerIdGenerator.nextId();

        // creation of the aggregate root...
        Instant now = Instant.now();
        DocumentType documentType = parseDocumentType(command.documentType());

        var customer = Customer.builder()
                .withCustomerId(customerId)
                .withFirstName(command.firstName())
                .withLastName(command.lastName())
                .withDocumentType(documentType)
                .withDocument(command.document())
                .withEmail(new Email(command.email()))
                .withPhone(new Phone(command.phone()))
                .withNow(now)
                .build();

        // perform persistence...
        customerRepository.save(customer);

        // stores domain events in outbox...
        outboxPublisher.store(customer.pullEvents());

        // returns the identifier code of aggregate root ...
        return customerId;
    }

    @Override
    @Transactional
    public CustomerId update(UpdateCustomerCommand command) {
        return null;
    }

    @Override
    @Transactional
    public void deactivate(DeactivateCustomerCommand command) {
        customerRepository.deactivate(new CustomerId(command.customerId()));
    }

    @Override
    @Transactional
    public void activate(ActivateCustomerCommand command) {
        customerRepository.activate(new CustomerId(command.customerId()));
    }

    @Override
    @Transactional
    public void block(BlockCustomerCommand command) {
        customerRepository.block(new CustomerId(command.customerId()));
    }

    private DocumentType parseDocumentType(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidCustomerException("Document type is required");
        }
        try {
            return DocumentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidCustomerException("Invalid document type: " + value);
        }
    }

    @Override
    public Optional<Customer> getById(GetCustomerCommand command) {
        return customerRepository.findById(new CustomerId(command.customerId()));
    }
}
