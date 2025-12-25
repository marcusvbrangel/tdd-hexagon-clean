package com.mvbr.retailstore.inventory.application.port.in;

import com.mvbr.retailstore.inventory.application.command.ReserveInventoryCommand;
import com.mvbr.retailstore.inventory.application.command.SagaContext;

/**
 * Porta de entrada do caso de uso de reserva de estoque.
 */
public interface ReserveInventoryUseCase {

    /**
     * Executa a reserva de estoque para um pedido.
     */
    void reserve(ReserveInventoryCommand command, SagaContext sagaContext);
}
