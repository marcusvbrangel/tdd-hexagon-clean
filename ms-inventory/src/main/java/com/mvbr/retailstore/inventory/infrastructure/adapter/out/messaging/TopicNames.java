package com.mvbr.retailstore.inventory.infrastructure.adapter.out.messaging;

/**
 * Nomes dos topicos Kafka usados pelo ms-inventory.
 */
public final class TopicNames {

    private TopicNames() {
    }

    public static final String INVENTORY_EVENTS_V1 = "inventory.events.v1";
    public static final String INVENTORY_COMMANDS_V1 = "inventory.commands.v1";
}
