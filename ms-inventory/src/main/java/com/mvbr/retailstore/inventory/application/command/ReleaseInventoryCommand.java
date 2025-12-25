package com.mvbr.retailstore.inventory.application.command;

/**
 * Comando interno para liberar reserva de estoque (compensacao).
 */
public record ReleaseInventoryCommand(
        String commandId,
        String orderId,
        String reason
) {
}
