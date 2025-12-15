package com.mvbr.estudo.tdd.application.port.in;

import com.mvbr.estudo.tdd.application.port.in.CreateOrderCommand;
import com.mvbr.estudo.tdd.application.port.in.CreateOrderItemCommand;
import java.math.BigDecimal;
import java.util.List;

public record PlaceOrderCommand(
        String customerId,
        List<PlaceOrderItemCommand> items,
        BigDecimal discount
) {

    public CreateOrderCommand toCreateOrder() {
        List<CreateOrderItemCommand> orderItems = items.stream()
                .map(item -> new CreateOrderItemCommand(
                        item.productId(),
                        item.quantity(),
                        item.price()
                ))
                .toList();

        return new CreateOrderCommand(
                customerId,
                orderItems,
                discount
        );
    }
}
