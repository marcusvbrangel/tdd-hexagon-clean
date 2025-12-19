package com.mvbr.retailstore.order.application.port.out;

import com.mvbr.retailstore.order.domain.event.DomainEvent;

public interface EventPublisher {

    void publish(DomainEvent event);

}
