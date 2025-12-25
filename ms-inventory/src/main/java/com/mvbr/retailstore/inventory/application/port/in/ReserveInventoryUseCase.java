package com.mvbr.retailstore.inventory.application.port.in;

import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;

public interface ReserveInventoryUseCase {

    void reserve(ReserveInventoryCommand command, SagaContext sagaContext);
}
