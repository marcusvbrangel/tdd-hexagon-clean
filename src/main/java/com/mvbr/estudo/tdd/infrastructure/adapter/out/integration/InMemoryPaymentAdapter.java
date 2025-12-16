package com.mvbr.estudo.tdd.infrastructure.adapter.out.integration;

import com.mvbr.estudo.tdd.application.port.out.PaymentGateway;
import com.mvbr.estudo.tdd.domain.model.Money;
import com.mvbr.estudo.tdd.domain.model.OrderId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryPaymentAdapter implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPaymentAdapter.class);
    private final Map<String, BigDecimal> startedPayments = new ConcurrentHashMap<>();

    @Override
    public void startPayment(OrderId orderId, Money total) {
        startedPayments.put(orderId.value(), total.toBigDecimal());
        log.info("Pagamento iniciado (in-memory) para pedido {} no valor {}", orderId.value(), total.toBigDecimal());
    }

    public BigDecimal getPaymentValue(String orderIdValue) {
        return startedPayments.get(orderIdValue);
    }
}
