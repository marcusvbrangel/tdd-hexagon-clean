package com.mvbr.estudo.tdd.application.port.out;

import com.mvbr.estudo.tdd.domain.model.Money;
import com.mvbr.estudo.tdd.domain.model.OrderId;

public interface PaymentGateway {

    void startPayment(OrderId orderId, Money total);
}
