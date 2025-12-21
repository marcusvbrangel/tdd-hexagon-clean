package com.mvbr.retailstore.customer.infrastructure.adapter.out.idgenerator;

import com.mvbr.retailstore.customer.domain.model.CustomerId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UuidCustomerIdGeneratorAdapterTest {

    @Test
    void nextIdReturnsUuidWrappedCustomerId() {
        UuidCustomerIdGeneratorAdapter generator = new UuidCustomerIdGeneratorAdapter();

        CustomerId customerId = generator.nextId();

        assertNotNull(customerId);
        assertDoesNotThrow(() -> UUID.fromString(customerId.value()));
    }
}
