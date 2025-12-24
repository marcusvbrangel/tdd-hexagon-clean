package com.mvbr.retailstore.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
/**
 * Ponto de entrada do microservico de orquestracao do checkout.
 * O Spring Boot inicializa os beans (consumidores Kafka, schedulers e adaptadores).
 */
public class CheckoutOrchestratorApplication {

    /**
     * Inicializa o contexto Spring e inicia o fluxo do orquestrador.
     * Chamado pelo runtime da JVM.
     */
    public static void main(String[] args) {
        SpringApplication.run(CheckoutOrchestratorApplication.class, args);
    }
}
