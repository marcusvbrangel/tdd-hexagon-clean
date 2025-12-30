package com.mvbr.retailstore.inventory.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InventoryInboxProperties.class)
public class InventoryInboxConfig {
}
