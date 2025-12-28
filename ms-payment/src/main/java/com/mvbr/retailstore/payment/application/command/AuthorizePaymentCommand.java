package com.mvbr.retailstore.payment.application.command;

/**
 * Comando de autorizacao de pagamento vindo da saga.
 */
public record AuthorizePaymentCommand(
        String commandId,
        String orderId,
        String customerId,
        String amount,
        String currency,
        String paymentMethod
) {
}
