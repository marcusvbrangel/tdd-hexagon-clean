package com.mvbr.retailstore.customer.application.port.in;

import com.mvbr.retailstore.customer.application.command.BlockCustomerCommand;

public interface BlockCustomerUseCase {
    void block(BlockCustomerCommand command);
}
