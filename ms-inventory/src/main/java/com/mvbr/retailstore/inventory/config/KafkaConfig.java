package com.mvbr.retailstore.inventory.config;

import com.mvbr.retailstore.inventory.infrastructure.adapter.out.kafka.TopicNames;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

/**
 * Configuracao de infraestrutura Kafka (topics e producer).
 */
@Configuration
public class KafkaConfig {

    /**
     * Cria topicos do inventory no ambiente local quando autoCreate esta habilitado.
     * Le de inventory.kafka.topics.*
     */
    @Bean
    @ConditionalOnProperty(
            prefix = "inventory.kafka.topics",
            name = "autoCreate",
            havingValue = "true",
            matchIfMissing = true
    )
    public KafkaAdmin.NewTopics inventoryTopics(InventoryKafkaTopicsProperties topicsProps) {
        int partitions = topicsProps.getPartitions();
        short replicationFactor = topicsProps.getReplicationFactor();

        return new KafkaAdmin.NewTopics(
                topic(TopicNames.INVENTORY_COMMANDS_V1, partitions, replicationFactor),
                topic(TopicNames.INVENTORY_EVENTS_V1, partitions, replicationFactor)
        );
    }

    /**
     * Helper para padronizar criacao de topicos.
     */
    private NewTopic topic(String name, int partitions, short replicationFactor) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    /**
     * ProducerFactory baseado nas propriedades do Spring Boot.
     */
    @Bean
    public ProducerFactory<String, String> producerFactory(
            KafkaProperties kafkaProperties,
            ObjectProvider<SslBundles> sslBundlesProvider
    ) {
        SslBundles sslBundles = sslBundlesProvider.getIfAvailable();
        Map<String, Object> props = kafkaProperties.buildProducerProperties(sslBundles);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * KafkaTemplate usado pelo OutboxRelay.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> pf) {
        return new KafkaTemplate<>(pf);
    }
}
