package com.mvbr.retailstore.order.application.port.in;

import com.mvbr.retailstore.order.application.command.PlaceOrderCommand;
import com.mvbr.retailstore.order.domain.model.OrderId;

public interface PlaceOrderUseCase {
    OrderId execute(PlaceOrderCommand command);
}
