package com.mvbr.retailstore.order.application.query;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryReadModel(
        String orderId,
        String customerId,
        String status,
        BigDecimal discount,
        BigDecimal total,
        String currency,
        Instant createdAt
) { }
