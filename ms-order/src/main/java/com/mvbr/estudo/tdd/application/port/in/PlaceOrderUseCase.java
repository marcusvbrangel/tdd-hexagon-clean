package com.mvbr.estudo.tdd.application.port.in;

import com.mvbr.estudo.tdd.application.command.PlaceOrderCommand;
import com.mvbr.estudo.tdd.domain.model.OrderId;

public interface PlaceOrderUseCase {
    OrderId execute(PlaceOrderCommand command);
}
