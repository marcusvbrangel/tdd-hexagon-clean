package com.mvbr.retailstore.checkout.application.port.out;

import java.util.Map;

public interface CommandPublisher {
    void publish(String topic, String key, String commandType, Object payload, Map<String, String> headers);
}
