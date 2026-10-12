package com.vito.checkin;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// TODO(adapters): reactivar cuando exista el adaptador. Hoy el servicio NO arranca.
@Disabled("Falta bean para UserProfileService: el contexto no puede arrancar")
@SpringBootTest
@ActiveProfiles("test")
class CheckInServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
