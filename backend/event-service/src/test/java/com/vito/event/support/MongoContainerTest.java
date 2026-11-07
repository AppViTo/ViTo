package com.vito.event.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * T003 (Ola 1): base de las pruebas de integración contra un MongoDB real, misma versión
 * mayor que Compose y producción. {@code @ServiceConnection} reemplaza {@code spring.mongodb.uri}.
 * Sin Docker, las pruebas se saltan en lugar de fallar (como en auth-service).
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class MongoContainerTest {

    @Container
    @ServiceConnection
    protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");
}
