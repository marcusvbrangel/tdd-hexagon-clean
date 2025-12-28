package com.mvbr.retailstore.payment.application.usecase;

import com.mvbr.retailstore.payment.application.command.AuthorizePaymentCommand;
import com.mvbr.retailstore.payment.application.command.SagaContext;
import com.mvbr.retailstore.payment.application.port.in.AuthorizePaymentUseCase;
import com.mvbr.retailstore.payment.application.service.PaymentCommandService;
import org.springframework.stereotype.Component;

/**
 * Implementacao simples do caso de uso de autorizacao.
 */
@Component
public class AuthorizePaymentUseCaseImpl implements AuthorizePaymentUseCase {

    private final PaymentCommandService service;

    public AuthorizePaymentUseCaseImpl(PaymentCommandService service) {
        this.service = service;
    }

    @Override
    public void authorize(AuthorizePaymentCommand command, SagaContext sagaContext) {
        service.authorize(command, sagaContext);
    }
}
