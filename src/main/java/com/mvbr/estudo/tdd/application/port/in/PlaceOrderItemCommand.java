package com.mvbr.estudo.tdd.application.port.in;

import java.math.BigDecimal;

public record PlaceOrderItemCommand(
        String productId,
        int quantity,
        BigDecimal price
) { }
