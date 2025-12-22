package com.mvbr.retailstore.customer.infrastructure.adapter.out.persistence;

import com.mvbr.retailstore.customer.domain.model.Customer;
import com.mvbr.retailstore.customer.domain.model.CustomerId;
import com.mvbr.retailstore.customer.domain.model.CustomerStatus;
import com.mvbr.retailstore.customer.domain.model.DocumentType;
import com.mvbr.retailstore.customer.domain.model.Email;
import com.mvbr.retailstore.customer.domain.model.Phone;
import com.mvbr.retailstore.customer.infrastructure.adapter.out.persistence.repository.CustomerJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class CustomerRepositoryAdapterTest {

    @Autowired
    private CustomerJpaRepository customerJpaRepository;

    @Test
    void saveAndFindByIdRoundTrip() {
        Instant now = Instant.parse("2025-01-01T10:00:00Z");
        Customer customer = newCustomer(now);

        CustomerRepositoryAdapter adapter = new CustomerRepositoryAdapter(customerJpaRepository);
        Customer saved = adapter.save(customer);

        assertNotNull(saved);
        assertEquals(customer.getCustomerId(), saved.getCustomerId());

        Customer loaded = adapter.findById(customer.getCustomerId()).orElseThrow();
        assertEquals(customer.getCustomerId(), loaded.getCustomerId());
        assertEquals("Joao", loaded.getFirstName());
        assertEquals("Silva", loaded.getLastName());
        assertEquals(DocumentType.CPF, loaded.getDocument().type());
        assertEquals("63977319957", loaded.getDocument().value());
        assertEquals("joao@example.com", loaded.getEmail().normalized());
        assertEquals("11987654321", loaded.getPhone().toString());
        assertEquals(CustomerStatus.ACTIVE, loaded.getCustomerStatus());
        assertEquals(now, loaded.getCreatedAt());
        assertEquals(now, loaded.getUpdatedAt());
    }

    @Test
    void deactivateAndActivateUpdateStatus() {
        Instant now = Instant.parse("2025-01-02T10:00:00Z");
        Customer customer = newCustomer(now);

        CustomerRepositoryAdapter adapter = new CustomerRepositoryAdapter(customerJpaRepository);
        adapter.save(customer);

        CustomerId customerId = customer.getCustomerId();
        adapter.deactivate(customerId);

        Customer inactive = adapter.findById(customerId).orElseThrow();
        assertEquals(CustomerStatus.INACTIVE, inactive.getCustomerStatus());
        Instant afterDeactivate = inactive.getUpdatedAt();

        adapter.activate(customerId);

        Customer active = adapter.findById(customerId).orElseThrow();
        assertEquals(CustomerStatus.ACTIVE, active.getCustomerStatus());
        assertFalse(active.getUpdatedAt().isBefore(afterDeactivate));
    }

    @Test
    void blockPersistsStatus() {
        Instant now = Instant.parse("2025-01-03T10:00:00Z");
        Customer customer = newCustomer(now);

        CustomerRepositoryAdapter adapter = new CustomerRepositoryAdapter(customerJpaRepository);
        Customer saved = adapter.save(customer);

        CustomerId customerId = saved.getCustomerId();
        Instant beforeBlock = saved.getUpdatedAt();
        adapter.block(customerId);

        Customer blocked = adapter.findById(customerId).orElseThrow();
        assertEquals(CustomerStatus.BLOCKED, blocked.getCustomerStatus());
        assertFalse(blocked.getUpdatedAt().isBefore(beforeBlock));
    }

    private Customer newCustomer(Instant now) {
        return Customer.createNew(
                new CustomerId("cust-" + now.getEpochSecond()),
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
