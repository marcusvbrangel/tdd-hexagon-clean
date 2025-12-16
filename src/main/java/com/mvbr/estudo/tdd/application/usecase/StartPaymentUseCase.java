package com.mvbr.estudo.tdd.application.usecase;

import com.mvbr.estudo.tdd.domain.model.OrderId;

public interface StartPaymentUseCase {

    void execute(OrderId orderId);
}
