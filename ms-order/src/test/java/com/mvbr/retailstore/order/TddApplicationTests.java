package com.mvbr.retailstore.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "outbox.relay.enabled=false",
        "outbox.retention.enabled=false",
        "spring.task.scheduling.enabled=false",
        "spring.kafka.bootstrap-servers=localhost:0"
})
class TddApplicationTests {

	@Test
	void contextLoads() {
	}

}
