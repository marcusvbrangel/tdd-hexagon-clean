package com.mvbr.retailstore.payment.config;

import com.mvbr.retailstore.payment.infrastructure.adapter.out.messaging.TopicNames;
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

/**
 * Configuracao de infraestrutura Kafka (topics e producer).
 */
@Configuration
public class KafkaConfig {

    /**
     * Cria topicos do payment no ambiente local quando autoCreate esta habilitado.
     */
    @Bean
    @ConditionalOnProperty(prefix = "kafka.topics", name = "autoCreate", havingValue = "true", matchIfMissing = true)
    public KafkaAdmin.NewTopics paymentTopics(
            @Value("${kafka.topics.partitions:3}") int partitions,
            @Value("${kafka.topics.replicationFactor:1}") short replicationFactor
    ) {
        return new KafkaAdmin.NewTopics(
                topic(TopicNames.PAYMENT_COMMANDS_V1, partitions, replicationFactor),
                topic(TopicNames.PAYMENT_EVENTS_V1, partitions, replicationFactor)
        );
    }

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
