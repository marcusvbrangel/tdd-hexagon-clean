package com.mvbr.retailstore.order.infrastructure.adapter.in.web.dto;

import com.mvbr.retailstore.order.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        String orderId,
        String customerId,
        OrderStatus status,
        BigDecimal discount,
        BigDecimal total,
        String currency,
        Instant createdAt
) { }
