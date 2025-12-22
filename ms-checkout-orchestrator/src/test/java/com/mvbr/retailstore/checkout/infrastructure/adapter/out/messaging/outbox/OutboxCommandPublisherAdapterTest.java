package com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.dto.InventoryReleaseCommandV1;
import com.mvbr.retailstore.checkout.infrastructure.adapter.out.messaging.headers.HeaderNames;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboxCommandPublisherAdapterTest {

    @Test
    void publish_persists_outbox_with_event_type_and_headers() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RepositoryCaptor captor = new RepositoryCaptor();
        OutboxCommandPublisherAdapter adapter = new OutboxCommandPublisherAdapter(captor.proxy(), objectMapper);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HeaderNames.EVENT_ID, "cmd-1");
        headers.put(HeaderNames.CORRELATION_ID, "corr-1");

        InventoryReleaseCommandV1 payload = new InventoryReleaseCommandV1(
                "cmd-1",
                "2025-01-01T00:00:00Z",
                "order-1"
        );

        adapter.publish("inventory.commands.v1", "order-1", "inventory.release", payload, headers);

        assertThat(captor.saveCalls).isEqualTo(1);
        OutboxMessageJpaEntity msg = captor.saved;
        assertThat(msg.getEventId()).isEqualTo("cmd-1");
        assertThat(msg.getAggregateId()).isEqualTo("order-1");
        assertThat(msg.getEventType()).isEqualTo("inventory.release");
        assertThat(msg.getTopic()).isEqualTo("inventory.commands.v1");

        Map<String, String> storedHeaders = objectMapper.readValue(msg.getHeadersJson(), new TypeReference<>() {});
        assertThat(storedHeaders.get(HeaderNames.EVENT_TYPE)).isEqualTo("inventory.release");
        assertThat(storedHeaders.get(HeaderNames.EVENT_ID)).isEqualTo("cmd-1");
    }

    @Test
    void publish_requires_event_id_header() {
        ObjectMapper objectMapper = new ObjectMapper();
        RepositoryCaptor captor = new RepositoryCaptor();
        OutboxCommandPublisherAdapter adapter = new OutboxCommandPublisherAdapter(captor.proxy(), objectMapper);

        Map<String, String> headers = new HashMap<>();

        assertThatThrownBy(() -> adapter.publish(
                "inventory.commands.v1",
                "order-1",
                "inventory.release",
                new InventoryReleaseCommandV1("cmd-1", "2025-01-01T00:00:00Z", "order-1"),
                headers
        )).isInstanceOf(IllegalArgumentException.class);

        assertThat(captor.saveCalls).isZero();
    }

    private static final class RepositoryCaptor implements InvocationHandler {
        private int saveCalls;
        private OutboxMessageJpaEntity saved;

        private OutboxJpaRepository proxy() {
            return (OutboxJpaRepository) Proxy.newProxyInstance(
                    OutboxJpaRepository.class.getClassLoader(),
                    new Class<?>[] { OutboxJpaRepository.class },
                    this
            );
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("save".equals(method.getName()) && args != null && args.length == 1) {
                saved = (OutboxMessageJpaEntity) args[0];
                saveCalls++;
                return saved;
            }

            Class<?> returnType = method.getReturnType();
            if (returnType.equals(boolean.class)) {
                return false;
            }
            if (returnType.equals(long.class)) {
                return 0L;
            }
            if (returnType.equals(int.class)) {
                return 0;
            }
            if (returnType.equals(void.class)) {
                return null;
            }
            return null;
        }
    }
}
