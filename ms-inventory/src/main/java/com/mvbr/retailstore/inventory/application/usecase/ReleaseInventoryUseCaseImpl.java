package com.mvbr.retailstore.inventory.application.usecase;

import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;
import com.mvbr.retailstore.inventory.application.port.in.ReleaseInventoryUseCase;
import com.mvbr.retailstore.inventory.application.service.InventoryCommandService;
import org.springframework.stereotype.Component;

/**
 * Implementacao simples do caso de uso de liberacao.
 * Mantem a classe fina e delega para o service.
 */
@Component
public class ReleaseInventoryUseCaseImpl implements ReleaseInventoryUseCase {

    private final InventoryCommandService service;

    public ReleaseInventoryUseCaseImpl(InventoryCommandService service) {
        this.service = service;
    }

    /**
     * Delegacao direta para o InventoryCommandService.
     */
    @Override
    public void release(ReleaseInventoryCommand command, SagaContext sagaContext) {
        service.release(command, sagaContext);
    }
}
