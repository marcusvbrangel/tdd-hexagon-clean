package com.mvbr.retailstore.customer.application.port.in;

import com.mvbr.retailstore.customer.application.command.ActivateCustomerCommand;

public interface ActivateCustomerUseCase {
    void activate(ActivateCustomerCommand command);
}
