package com.mvbr.estudo.tdd.application.port.in;

import com.mvbr.estudo.tdd.domain.model.Order;

public interface CancelOrderUseCase {
    Order cancel(String orderId);
}
