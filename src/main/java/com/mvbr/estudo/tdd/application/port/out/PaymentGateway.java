package com.mvbr.estudo.tdd.application.port.out;

import java.math.BigDecimal;

public interface PaymentGateway {

    void startPayment(String orderId, BigDecimal total);
}
