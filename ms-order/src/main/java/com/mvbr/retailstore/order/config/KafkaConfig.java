package com.mvbr.retailstore.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

@Configuration
public class KafkaConfig {

    // Topic único (canal) para todos os eventos do domínio Order
    public static final String TOPIC_ORDER_EVENTS_V1 = "order.events.v1";
    public static final String TOPIC_ORDER_COMMANDS_V1 = "order.commands.v1";

    // ============================
    // Topic auto-create (DEV)
    // ============================
    @Bean
    @ConditionalOnProperty(prefix = "kafka.topics", name = "autoCreate", havingValue = "true", matchIfMissing = true)
    public KafkaAdmin.NewTopics orderTopics(
            @Value("${kafka.topics.partitions:3}") int partitions,
            @Value("${kafka.topics.replicationFactor:1}") short replicationFactor
    ) {
        NewTopic orderEvents = TopicBuilder.name(TOPIC_ORDER_EVENTS_V1)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
        NewTopic orderCommands = TopicBuilder.name(TOPIC_ORDER_COMMANDS_V1)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();

        return new KafkaAdmin.NewTopics(orderEvents, orderCommands);
    }

    // ============================
    // Producer (100% YAML-driven)
    // ============================
    @Bean
    public ProducerFactory<String, String> producerFactory(
            KafkaProperties kafkaProperties,
            ObjectProvider<SslBundles> sslBundlesProvider
    ) {
        SslBundles sslBundles = sslBundlesProvider.getIfAvailable();
        Map<String, Object> props = kafkaProperties.buildProducerProperties(sslBundles);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }
}
