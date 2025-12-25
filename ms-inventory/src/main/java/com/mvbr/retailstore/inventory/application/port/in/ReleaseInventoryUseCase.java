package com.mvbr.retailstore.inventory.application.port.in;

import com.mvbr.retailstore.inventory.application.command.ReleaseInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;

/**
 * Porta de entrada do caso de uso de liberacao de reserva.
 */
public interface ReleaseInventoryUseCase {

    /**
     * Executa a liberacao de estoque para um pedido.
     */
    void release(ReleaseInventoryCommand command, SagaContext sagaContext);
}
