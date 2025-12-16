package com.mvbr.estudo.tdd.application.query;

import java.math.BigDecimal;
import java.util.List;

public record OrderReadModel(
        String orderId,
        String customerId,
        String status,
        BigDecimal discount,
        BigDecimal total,
        List<OrderItemReadModel> items
) { }
