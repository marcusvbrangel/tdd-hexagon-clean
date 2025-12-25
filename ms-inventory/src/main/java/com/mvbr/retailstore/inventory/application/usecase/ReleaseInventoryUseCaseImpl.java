package com.mvbr.retailstore.inventory.application.usecase;

import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.ReleaseInventoryUseCase;
import com.mvbr.retailstore.inventory.application.service.InventoryCommandService;
import org.springframework.stereotype.Component;

@Component
public class ReleaseInventoryUseCaseImpl implements ReleaseInventoryUseCase {

    private final InventoryCommandService service;

    public ReleaseInventoryUseCaseImpl(InventoryCommandService service) {
        this.service = service;
    }

    @Override
    public void release(ReleaseInventoryCommand command, SagaContext sagaContext) {
        service.release(command, sagaContext);
    }
}
