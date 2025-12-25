package com.mvbr.retailstore.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do microservico de inventory.
 * Habilita jobs de expiracao e relays agendados.
 */
@SpringBootApplication
@EnableScheduling
public class InventoryApplication {

    /**
     * Inicializa o contexto Spring do ms-inventory.
     */
    public static void main(String[] args) {
        SpringApplication.run(InventoryApplication.class, args);
    }
}
