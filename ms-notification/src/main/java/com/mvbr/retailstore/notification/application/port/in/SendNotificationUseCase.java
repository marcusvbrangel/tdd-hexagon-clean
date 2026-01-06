package com.mvbr.retailstore.notification.application.port.in;

import com.mvbr.retailstore.notification.application.command.SendNotificationCommand;
import com.mvbr.retailstore.notification.application.command.SagaContext;

public interface SendNotificationUseCase {
    void send(SendNotificationCommand command, SagaContext sagaContext);
}
