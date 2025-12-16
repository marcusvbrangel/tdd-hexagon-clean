package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto;

import com.mvbr.estudo.tdd.domain.model.OrderStatus;

import java.math.BigDecimal;

public record OrderSummaryResponse(
        String orderId,
        String customerId,
        OrderStatus status,
        BigDecimal discount,
        BigDecimal total
) { }
