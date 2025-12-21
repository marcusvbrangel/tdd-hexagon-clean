package com.mvbr.retailstore.customer.application.port.in;

import com.mvbr.retailstore.customer.application.command.CreateCustomerCommand;
import com.mvbr.retailstore.customer.domain.model.CustomerId;

public interface CreateCustomerUseCase {
    CustomerId create(CreateCustomerCommand command);
}
