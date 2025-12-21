package com.mvbr.retailstore.customer.application.port.in;

import com.mvbr.retailstore.customer.application.command.UpdateCustomerCommand;
import com.mvbr.retailstore.customer.domain.model.CustomerId;

public interface UpdateCustomerUseCase {
    CustomerId update(UpdateCustomerCommand command);
}
