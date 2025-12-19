package com.mvbr.retailstore.order.application.command;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record PlaceOrderCommand(
        String customerId,
        List<PlaceOrderItemCommand> items,
        Optional<BigDecimal> discount
) {

    public PlaceOrderCommand toCreateOrder() {
        List<PlaceOrderItemCommand> orderItems = items.stream()
                .map(item -> new PlaceOrderItemCommand(
                        item.productId(),
                        item.quantity(),
                        item.price()
                ))
                .toList();

        return new PlaceOrderCommand(
                customerId,
                orderItems,
                discount
        );
    }
}
