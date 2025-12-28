package com.mvbr.retailstore.inventory.application.port.in;

import com.mvbr.retailstore.inventory.application.command.CommitInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;

/**
 * Porta de entrada para efetivar reservas de estoque.
 */
public interface CommitInventoryUseCase {

    void commit(CommitInventoryCommand command, SagaContext sagaContext);
}
