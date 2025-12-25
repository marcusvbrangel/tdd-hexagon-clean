package com.mvbr.retailstore.inventory.application.command;

public record ReserveInventoryItemCommand(
        String productId,
        long quantity
) {
}
