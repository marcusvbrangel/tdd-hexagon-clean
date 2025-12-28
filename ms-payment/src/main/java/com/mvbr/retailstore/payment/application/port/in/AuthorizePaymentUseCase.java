package com.mvbr.retailstore.payment.application.port.in;

import com.mvbr.retailstore.payment.application.command.AuthorizePaymentCommand;
import com.mvbr.retailstore.payment.application.command.SagaContext;

/**
 * Porta de entrada para autorizacao de pagamento.
 */
public interface AuthorizePaymentUseCase {

    void authorize(AuthorizePaymentCommand command, SagaContext sagaContext);
}
