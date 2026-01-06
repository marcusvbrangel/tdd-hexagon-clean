package com.mvbr.retailstore.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    public RestClient customerRestClient(NotificationProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getCustomer().getBaseUrl())
                .build();
    }
}
