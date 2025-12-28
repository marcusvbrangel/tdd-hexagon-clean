package com.mvbr.retailstore.payment.application.command;

/**
 * Comando de captura de pagamento.
 */
public record CapturePaymentCommand(
        String commandId,
        String orderId,
        String paymentId
) {
}
