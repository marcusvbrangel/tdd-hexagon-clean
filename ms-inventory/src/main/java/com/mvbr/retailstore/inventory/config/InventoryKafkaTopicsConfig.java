package com.mvbr.retailstore.inventory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InventoryKafkaTopicsProperties.class)
public class InventoryKafkaTopicsConfig {
}
