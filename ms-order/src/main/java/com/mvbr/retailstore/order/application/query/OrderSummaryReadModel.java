package com.mvbr.retailstore.order.application.query;

import java.math.BigDecimal;

public record OrderSummaryReadModel(
        String orderId,
        String customerId,
        String status,
        BigDecimal discount,
        BigDecimal total
) { }
