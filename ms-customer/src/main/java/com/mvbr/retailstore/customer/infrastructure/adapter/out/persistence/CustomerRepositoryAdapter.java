package com.mvbr.retailstore.customer.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.customer.application.port.out.CustomerRepository;
import com.mvbr.retailstore.customer.domain.model.Customer;
import com.mvbr.retailstore.customer.domain.model.CustomerId;
import com.mvbr.retailstore.customer.domain.model.CustomerStatus;
import com.mvbr.retailstore.customer.domain.model.DocumentType;
import com.mvbr.retailstore.customer.domain.model.Email;
import com.mvbr.retailstore.customer.domain.model.Phone;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.persistence.entity.CustomerJpaEntity;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.persistence.repository.CustomerJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
public class CustomerRepositoryAdapter implements CustomerRepository {

    private final CustomerJpaRepository customerRepository;

    public CustomerRepositoryAdapter(CustomerJpaRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    public Customer save(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer is required");
        }
        CustomerJpaEntity saved = customerRepository.save(toEntity(customer));
        return toDomain(saved);
    }

    @Override
    public Optional<Customer> findById(CustomerId customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer id is required");
        }
        return customerRepository.findById(customerId.value()).map(this::toDomain);
    }

    @Override
    public void activate(CustomerId customerId) {
        updateStatus(customerId, CustomerStatus.ACTIVE);
    }

    @Override
    public void deactivate(CustomerId customerId) {
        updateStatus(customerId, CustomerStatus.INACTIVE);
    }

    @Override
    public void block(Customer customer) {
        if (customer == null) {
            throw new IllegalArgumentException("Customer is required");
        }
        customerRepository.save(toEntity(customer));
    }

    private void updateStatus(CustomerId customerId, CustomerStatus status) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer id is required");
        }
        CustomerJpaEntity entity = customerRepository.findById(customerId.value())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId.value()));
        entity.setStatus(status);
        entity.setUpdatedAt(Instant.now());
        customerRepository.save(entity);
    }

    private CustomerJpaEntity toEntity(Customer customer) {
        return new CustomerJpaEntity(
                customer.getCustomerId().value(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getDocument().type(),
                customer.getDocument().value(),
                customer.getEmail().value(),
                customer.getPhone().value(),
                customer.getCustomerStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    private Customer toDomain(CustomerJpaEntity entity) {
        return Customer.restore(
                new CustomerId(entity.getCustomerId()),
                entity.getFirstName(),
                entity.getLastName(),
                resolveDocumentType(entity.getDocumentType()),
                entity.getDocument(),
                new Email(entity.getEmail()),
                new Phone(entity.getPhone()),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private DocumentType resolveDocumentType(DocumentType documentType) {
        if (documentType == null) {
            throw new IllegalArgumentException("Document type is required");
        }
        return documentType;
    }
}
