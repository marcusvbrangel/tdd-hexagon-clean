package com.mvbr.retailstore.customer.application.port.in;

import com.mvbr.retailstore.customer.application.command.DeactivateCustomerCommand;

public interface DeactivateCustomerUseCase {
    void deactivate(DeactivateCustomerCommand command);
}
