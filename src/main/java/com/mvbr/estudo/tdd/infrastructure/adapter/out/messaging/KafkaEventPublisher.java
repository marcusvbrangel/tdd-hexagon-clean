package com.mvbr.estudo.tdd.infrastructure.adapter.out.messaging;

import com.mvbr.estudo.tdd.application.port.out.EventPublisher;
import com.mvbr.estudo.tdd.domain.event.DomainEvent;
import com.mvbr.estudo.tdd.domain.event.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    @Override
    public void publish(DomainEvent event) {

        log.info("==========================================================");
        log.info("PUBLISHING EVENT TO KAFKA");
        log.info("Event ID: {}", event.eventId());
        log.info("Event Type: {}", event.eventType());
        log.info("Event Occurred at: {}", event.occurredAt());
        log.info(".......................");
        log.info("Order ID: {}", event instanceof OrderPlacedEvent ope ? ope.orderId() : "N/A");
        log.info("Customer ID: {}", event instanceof OrderPlacedEvent ope ? ope.customerId() : "N/A");
        log.info("Product IDs: {}", event instanceof OrderPlacedEvent ope ? ope.productIds() : "N/A");
        log.info("==========================================================");

    }

}
