package com.mvbr.retailstore.order.application.port.in;

import com.mvbr.retailstore.order.domain.model.Order;

public interface CompleteOrderUseCase {
    Order complete(String orderId);
}
