package com.mvbr.retailstore.payment.application.port.out;

/**
 * Resultado da autorizacao retornado pelo gateway.
 */
public record PaymentAuthorizationResult(
        boolean authorized,
        String providerPaymentId,
        String status,
        String declineReason
) {

    public static PaymentAuthorizationResult declined(String reason) {
        return new PaymentAuthorizationResult(false, null, null, reason);
    }
}
