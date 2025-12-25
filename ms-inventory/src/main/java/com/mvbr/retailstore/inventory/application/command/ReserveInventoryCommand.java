package com.mvbr.retailstore.inventory.application.command;

import java.util.List;

/**
 * Comando interno para reservar estoque de um pedido.
 * Criado pelo consumer a partir do comando Kafka.
 */
public record ReserveInventoryCommand(
        String commandId,
        String orderId,
        List<ReserveInventoryItemCommand> items
) {
}
