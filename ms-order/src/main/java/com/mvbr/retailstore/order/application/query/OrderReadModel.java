package com.mvbr.retailstore.order.application.query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderReadModel(
        String orderId,
        String customerId,
        String status,
        BigDecimal discount,
        BigDecimal total,
        String currency,
        Instant createdAt,
        List<OrderItemReadModel> items
) { }
