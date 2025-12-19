package com.mvbr.retailstore.order.application.command;

import java.math.BigDecimal;

public record PlaceOrderItemCommand(
    String productId,
    int quantity,
    BigDecimal price
) { }
