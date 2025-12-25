package com.mvbr.retailstore.inventory.application.command;

/**
 * Item de reserva recebido no comando de inventario.
 */
public record ReserveInventoryItemCommand(
        String productId,
        long quantity
) {
}
