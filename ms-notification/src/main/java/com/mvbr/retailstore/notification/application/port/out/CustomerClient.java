package com.mvbr.retailstore.notification.application.port.out;

import java.util.Optional;

public interface CustomerClient {
    Optional<String> findEmailById(String customerId);
}
