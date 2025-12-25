package com.mvbr.retailstore.inventory.application.usecase;

import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.ReserveInventoryUseCase;
import com.mvbr.retailstore.inventory.application.service.InventoryCommandService;
import org.springframework.stereotype.Component;

@Component
public class ReserveInventoryUseCaseImpl implements ReserveInventoryUseCase {

    private final InventoryCommandService service;

    public ReserveInventoryUseCaseImpl(InventoryCommandService service) {
        this.service = service;
    }

    @Override
    public void reserve(ReserveInventoryCommand command, SagaContext sagaContext) {
        service.reserve(command, sagaContext);
    }
}
