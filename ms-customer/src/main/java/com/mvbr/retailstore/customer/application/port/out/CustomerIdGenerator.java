package com.mvbr.retailstore.customer.application.port.out;

import com.mvbr.retailstore.customer.domain.model.CustomerId;

public interface CustomerIdGenerator {
    CustomerId nextId();
}
