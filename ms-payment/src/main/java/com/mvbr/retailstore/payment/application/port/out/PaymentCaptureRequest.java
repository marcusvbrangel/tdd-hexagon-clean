package com.mvbr.retailstore.payment.application.port.out;

/**
 * Dados necessarios para captura de pagamento no gateway.
 */
public record PaymentCaptureRequest(
        String commandId,
        String orderId,
        String paymentId,
        String providerPaymentId,
        String correlationId,
        String sagaId
) {
}
