package com.mvbr.retailstore.payment.application.port.in;

import com.mvbr.retailstore.payment.application.command.CapturePaymentCommand;
import com.mvbr.retailstore.payment.application.command.SagaContext;

/**
 * Porta de entrada para captura de pagamento.
 */
public interface CapturePaymentUseCase {

    void capture(CapturePaymentCommand command, SagaContext sagaContext);
}
