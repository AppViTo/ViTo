package com.vito.media.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

/** MongoDB real para los adaptadores (mismo patrón que event-service y social-service). */
@Testcontainers(disabledWithoutDocker = true)
public abstract class MongoContainerTest {

    @Container
    @ServiceConnection
    protected static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");
}
