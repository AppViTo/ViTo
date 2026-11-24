package com.vito.social.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/**
 * T003 (Ola 2a): base de las pruebas de integración contra un MongoDB real, misma versión
 * mayor que Compose y producción. {@code @ServiceConnection} reemplaza {@code spring.mongodb.uri}.
 * Sin Docker, las pruebas se saltan en lugar de fallar (como en auth-service y event-service).
 * <p>
 * El contenedor se levanta y se para por clase. {@code @DirtiesContext} cierra el contexto de
 * Spring al terminar cada clase: si no, dos clases con la misma configuración (p. ej.
 * {@code FollowMongoAdapterIntegrationTest} y {@code FollowConcurrencyIntegrationTest}) comparten
 * el contexto en caché y la segunda apunta al puerto de un contenedor ya parado.
 */
@DirtiesContext
@Testcontainers(disabledWithoutDocker = true)
public abstract class MongoContainerTest {

    @Container
    @ServiceConnection
    protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");
}
