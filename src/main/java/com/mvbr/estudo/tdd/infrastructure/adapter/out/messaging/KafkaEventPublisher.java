package com.mvbr.estudo.tdd.infrastructure.adapter.out.messaging;

import com.mvbr.estudo.tdd.application.port.out.EventPublisher;
import com.mvbr.estudo.tdd.domain.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    @Override
    public void publish(DomainEvent event) {
        log.info("Publishing event: {}", event);
    }

}
