package com.mvbr.retailstore.inventory.application.port.in;

import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;

public interface ReleaseInventoryUseCase {

    void release(ReleaseInventoryCommand command, SagaContext sagaContext);
}
