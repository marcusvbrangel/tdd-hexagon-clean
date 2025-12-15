package com.mvbr.estudo.tdd.infrastructure.adapter.in.web.dto;

import com.mvbr.estudo.tdd.domain.model.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponse(
        String orderId,
        String customerId,
        OrderStatus status,
        BigDecimal discount,
        BigDecimal total,
        List<OrderItemResponse> items
) { }
