package com.mvbr.retailstore.customer.config;

import com.mvbr.retailstore.customer.application.port.out.CustomerIdGenerator;
import com.mvbr.retailstore.customer.application.port.out.CustomerRepository;
import com.mvbr.retailstore.customer.application.port.out.OutboxPublisher;
import com.mvbr.retailstore.customer.application.service.CustomerCommandService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfig {

    @Bean
    public CustomerCommandService customerCommandService(
            CustomerRepository customerRepository,
            CustomerIdGenerator customerIdGenerator,
            OutboxPublisher outboxPublisher
    ) {
        return new CustomerCommandService(customerRepository, customerIdGenerator, outboxPublisher);
    }
}
