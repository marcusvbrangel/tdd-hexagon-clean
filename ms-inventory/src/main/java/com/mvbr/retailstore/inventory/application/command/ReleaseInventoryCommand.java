package com.mvbr.retailstore.inventory.application.command;

public record ReleaseInventoryCommand(
        String commandId,
        String orderId,
        String reason
) {
}
