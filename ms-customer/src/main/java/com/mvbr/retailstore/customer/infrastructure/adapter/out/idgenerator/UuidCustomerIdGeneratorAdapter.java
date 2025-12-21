package com.mvbr.retailstore.customer.infrastructure.adapter.out.idgenerator;

import com.mvbr.retailstore.customer.application.port.out.CustomerIdGenerator;
import com.mvbr.retailstore.customer.domain.model.CustomerId;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidCustomerIdGeneratorAdapter implements CustomerIdGenerator {

    @Override
    public CustomerId nextId() {
        return new CustomerId(UUID.randomUUID().toString());
    }
}
