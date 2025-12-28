package com.mvbr.retailstore.payment.application.port.out;

import java.math.BigDecimal;

/**
 * Dados necessários para autorizacao de pagamento no gateway.
 */
public record PaymentAuthorizationRequest(
        String commandId,
        String orderId,
        String customerId,
        BigDecimal amount,
        String currency,
        String paymentMethod,
        String correlationId,
        String sagaId
) {
}
