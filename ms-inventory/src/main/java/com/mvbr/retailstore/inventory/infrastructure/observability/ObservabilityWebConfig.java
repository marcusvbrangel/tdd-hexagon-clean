package com.mvbr.retailstore.inventory.infrastructure.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityWebConfig {

    @Bean
    public HttpCorrelationFilter httpCorrelationFilter() {
        return new HttpCorrelationFilter();
    }
}
