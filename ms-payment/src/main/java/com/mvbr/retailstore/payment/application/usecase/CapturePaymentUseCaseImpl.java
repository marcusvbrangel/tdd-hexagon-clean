package com.mvbr.retailstore.payment.application.usecase;

import com.mvbr.retailstore.payment.application.command.CapturePaymentCommand;
import com.mvbr.retailstore.payment.application.command.SagaContext;
import com.mvbr.retailstore.payment.application.port.in.CapturePaymentUseCase;
import com.mvbr.retailstore.payment.application.service.PaymentCommandService;
import org.springframework.stereotype.Component;

/**
 * Implementacao simples do caso de uso de captura.
 */
@Component
public class CapturePaymentUseCaseImpl implements CapturePaymentUseCase {

    private final PaymentCommandService service;

    public CapturePaymentUseCaseImpl(PaymentCommandService service) {
        this.service = service;
    }

    @Override
    public void capture(CapturePaymentCommand command, SagaContext sagaContext) {
        service.capture(command, sagaContext);
    }
}
