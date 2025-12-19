package com.mvbr.retailstore.order.application.query;

import java.math.BigDecimal;
import java.util.Optional;

public record OrderQueryFilters(
        Optional<String> status,
        Optional<String> customerId,
        Optional<BigDecimal> minTotal,
        Optional<BigDecimal> maxTotal
) {
    public static OrderQueryFilters empty() {
        return new OrderQueryFilters(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
