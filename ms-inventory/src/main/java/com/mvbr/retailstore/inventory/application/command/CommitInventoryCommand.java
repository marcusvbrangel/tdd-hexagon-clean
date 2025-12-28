package com.mvbr.retailstore.inventory.application.command;

/**
 * Comando interno para efetivar reserva de estoque.
 */
public record CommitInventoryCommand(
        String commandId,
        String orderId
) {
}
