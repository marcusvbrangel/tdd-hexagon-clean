package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        String productId,
        int quantity,
        BigDecimal price,
        BigDecimal subTotal
) { }
