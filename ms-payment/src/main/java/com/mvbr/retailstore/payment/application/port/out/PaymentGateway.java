package com.mvbr.retailstore.payment.application.port.out;

/**
 * Porta para integracao com gateway de pagamentos.
 */
public interface PaymentGateway {

    PaymentAuthorizationResult authorize(PaymentAuthorizationRequest request);

    PaymentCaptureResult capture(PaymentCaptureRequest request);
}
