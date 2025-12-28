package com.mvbr.retailstore.inventory.application.usecase;

import com.mvbr.retailstore.inventory.application.command.CommitInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.CommitInventoryUseCase;
import com.mvbr.retailstore.inventory.application.service.InventoryCommandService;
import org.springframework.stereotype.Component;

/**
 * Implementacao simples do caso de uso de commit de estoque.
 */
@Component
public class CommitInventoryUseCaseImpl implements CommitInventoryUseCase {

    private final InventoryCommandService service;

    public CommitInventoryUseCaseImpl(InventoryCommandService service) {
        this.service = service;
    }

    @Override
    public void commit(CommitInventoryCommand command, SagaContext sagaContext) {
        service.commit(command, sagaContext);
    }
}
