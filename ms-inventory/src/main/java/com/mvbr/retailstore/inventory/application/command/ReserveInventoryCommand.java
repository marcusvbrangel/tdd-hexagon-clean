package com.mvbr.retailstore.inventory.application.command;

import java.util.List;

public record ReserveInventoryCommand(
        String commandId,
        String orderId,
        List<ReserveInventoryItemCommand> items
) {
}
