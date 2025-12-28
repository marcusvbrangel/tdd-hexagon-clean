package com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging;

/**
 * Nomes dos topicos Kafka usados pelo ms-payment.
 */
public final class TopicNames {

    private TopicNames() {
    }

    public static final String PAYMENT_EVENTS_V1 = "payment.events.v1";
    public static final String PAYMENT_COMMANDS_V1 = "payment.commands.v1";
}
