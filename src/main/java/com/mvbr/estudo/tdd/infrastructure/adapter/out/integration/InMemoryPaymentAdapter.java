package com.mvbr.estudo.tdd.infrastructure.adapter.out.integration;

import com.mvbr.estudo.tdd.application.port.out.PaymentGateway;
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
    public void startPayment(String orderId, BigDecimal total) {
        startedPayments.put(orderId, total);
        log.info("Pagamento iniciado (in-memory) para pedido {} no valor {}", orderId, total);
    }

    public BigDecimal getPaymentValue(String orderId) {
        return startedPayments.get(orderId);
    }
}
