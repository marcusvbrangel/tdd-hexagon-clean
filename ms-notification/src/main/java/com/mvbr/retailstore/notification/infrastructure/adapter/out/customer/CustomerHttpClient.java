package com.mvbr.retailstore.notification.infrastructure.adapter.out.customer;

import com.mvbr.retailstore.notification.application.port.out.CustomerClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;
import java.util.logging.Logger;

@Component
public class CustomerHttpClient implements CustomerClient {

    private static final Logger log = Logger.getLogger(CustomerHttpClient.class.getName());

    private final RestClient restClient;

    public CustomerHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<String> findEmailById(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return Optional.empty();
        }
        try {
            CustomerResponse response = restClient.get()
                    .uri("/customers/{customerId}", customerId)
                    .retrieve()
                    .body(CustomerResponse.class);
            if (response == null || response.email() == null || response.email().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(response.email());
        } catch (RestClientResponseException ex) {
            log.warning("Customer service error status=" + ex.getRawStatusCode() + " customerId=" + customerId);
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warning("Customer service unavailable customerId=" + customerId + " error=" + ex.getMessage());
            return Optional.empty();
        }
    }
}
