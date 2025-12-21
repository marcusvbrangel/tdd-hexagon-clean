package com.mvbr.retailstore.customer.config;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaAdmin.NewTopics;

@Configuration
public class KafkaTopicConfig {

    private static final String CUSTOMER_CREATED_TOPIC = "customer.created";
    private static final String CUSTOMER_CHANGED_TOPIC = "customer.changed";

    @Bean
    public KafkaAdmin kafkaAdmin(KafkaProperties properties) {
        KafkaAdmin admin = new KafkaAdmin(properties.buildAdminProperties(null));
        admin.setAutoCreate(true);
        admin.setModifyTopicConfigs(true);
        return admin;
    }

    @Bean
    public NewTopics customerTopics() {
        return new NewTopics(
                TopicBuilder.name(CUSTOMER_CREATED_TOPIC)
                        .partitions(1)
                        .replicas((short) 1)
                        .config("min.insync.replicas", "1")
                        .build(),
                TopicBuilder.name(CUSTOMER_CHANGED_TOPIC)
                        .partitions(1)
                        .replicas((short) 1)
                        .config("min.insync.replicas", "1")
                        .build()
        );
    }
}
