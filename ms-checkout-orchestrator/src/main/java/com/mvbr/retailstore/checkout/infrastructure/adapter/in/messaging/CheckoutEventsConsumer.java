package com.mvbr.retailstore.checkout.infrastructure.adapter.in.messaging;

import com.mvbr.retailstore.checkout.application.service.CheckoutSagaEngine;
import com.mvbr.retailstore.checkout.infrastructure.adapter.in.messaging.envelope.EventEnvelope;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.TopicNames;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.logging.Logger;

@Component
/**
 * Adaptador de entrada Kafka: recebe eventos e entrega ao motor da saga.
 */
public class CheckoutEventsConsumer {

    private static final Logger log = Logger.getLogger(CheckoutEventsConsumer.class.getName());

    private final CheckoutSagaEngine sagaEngine;

    public CheckoutEventsConsumer(CheckoutSagaEngine sagaEngine) {
        this.sagaEngine = sagaEngine;
    }

    /**
     * Listener do Kafka para eventos de order/inventory/payment.
     * Fluxo: Kafka -> CheckoutEventsConsumer -> CheckoutSagaEngine.handle().
     */
    @KafkaListener(
            topics = {
                    TopicNames.ORDER_EVENTS_V1,
                    TopicNames.INVENTORY_EVENTS_V1,
                    TopicNames.PAYMENT_EVENTS_V1
            },
            groupId = "${spring.kafka.consumer.group-id:ms-checkout-orchestrator}"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope env = EventEnvelope.from(record);

        if (env.eventType() == null || env.eventType().isBlank()) {
            log.warning("Ignoring message without x-event-type. topic=" + env.topic() + " key=" + env.key());
            return;
        }
        if (env.eventId() == null || env.eventId().isBlank()) {
            log.warning("Ignoring message without x-event-id. topic=" + env.topic() + " key=" + env.key());
            return;
        }

        sagaEngine.handle(env);
    }
}
