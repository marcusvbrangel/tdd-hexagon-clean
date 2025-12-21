package com.mvbr.retailstore.customer.application.port.out;

import com.mvbr.retailstore.customer.domain.model.Customer;
import com.mvbr.retailstore.customer.domain.model.CustomerId;

import java.util.Optional;

public interface CustomerRepository {

    Customer save(Customer customer);

    Optional<Customer> findById(CustomerId customerId);

    void activate(CustomerId customerId);

    void deactivate(CustomerId customerId);

    void block(Customer customer);

}
