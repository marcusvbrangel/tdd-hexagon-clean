package com.mvbr.retailstore.shipping.infrastructure.observability;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityWebConfig {

    @Bean
    public HttpCorrelationFilter httpCorrelationFilter() {
        return new HttpCorrelationFilter();
    }
}
