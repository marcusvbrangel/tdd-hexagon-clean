package com.mvbr.retailstore.customer.application.port.in;

import com.mvbr.retailstore.customer.application.command.GetCustomerCommand;
import com.mvbr.retailstore.customer.domain.model.Customer;

import java.util.Optional;

public interface GetCustomerUseCase {
    Optional<Customer> getById(GetCustomerCommand command);
}
