package com.mvbr.retailstore.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CheckoutOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckoutOrchestratorApplication.class, args);
    }
}
