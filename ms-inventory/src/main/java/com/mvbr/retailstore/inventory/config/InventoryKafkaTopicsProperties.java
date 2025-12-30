package com.mvbr.retailstore.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory.kafka.topics")
public class InventoryKafkaTopicsProperties {

    /**
     * Se true, cria tópicos automaticamente (DEV/local).
     */
    private boolean autoCreate = true;

    /**
     * Número de partições.
     */
    private int partitions = 3;

    /**
     * Replication factor (short).
     */
    private short replicationFactor = 1;

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public int getPartitions() {
        return partitions;
    }

    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }

    public short getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(short replicationFactor) {
        this.replicationFactor = replicationFactor;
    }
}
