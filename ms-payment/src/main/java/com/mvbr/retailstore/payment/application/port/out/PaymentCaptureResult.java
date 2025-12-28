package com.mvbr.retailstore.payment.application.port.out;

/**
 * Resultado da captura retornado pelo gateway.
 */
public record PaymentCaptureResult(
        boolean captured,
        String providerPaymentId,
        String status,
        String failureReason
) {

    public static PaymentCaptureResult failed(String providerPaymentId, String status, String reason) {
        return new PaymentCaptureResult(false, providerPaymentId, status, reason);
    }
}
