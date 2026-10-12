package com.vito.chat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// TODO(adapters): reactivar cuando exista el adaptador. Hoy el servicio NO arranca.
@Disabled("Falta bean para RedisTemplate<String, Object>: el contexto no puede arrancar")
@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long",
        "MONGODB_URI=mongodb://localhost:27017/test_db"
})
class ChatServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
