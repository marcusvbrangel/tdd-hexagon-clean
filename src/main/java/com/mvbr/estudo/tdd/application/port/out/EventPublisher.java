package com.mvbr.estudo.tdd.application.port.out;

import com.mvbr.estudo.tdd.domain.event.DomainEvent;

public interface EventPublisher {

    void publish(DomainEvent event);

}
