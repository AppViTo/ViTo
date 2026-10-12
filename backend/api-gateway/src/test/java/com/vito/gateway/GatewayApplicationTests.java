package com.vito.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long"
})
class GatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
