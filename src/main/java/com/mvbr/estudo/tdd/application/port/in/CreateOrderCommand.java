package com.mvbr.estudo.tdd.application.port.in;

import java.math.BigDecimal;
import java.util.List;

public record CreateOrderCommand(
    String customerId,
    List<CreateOrderItemCommand> items,
    BigDecimal discount
) {}
