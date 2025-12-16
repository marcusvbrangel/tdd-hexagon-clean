package com.mvbr.estudo.tdd.application.query;

import java.math.BigDecimal;

public record OrderItemReadModel(
        String productId,
        int quantity,
        BigDecimal price,
        BigDecimal subTotal
) { }
