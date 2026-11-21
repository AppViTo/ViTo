package com.vito.auth.infrastructure.adapter.outbound.persistence;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.vito.auth.domain.model.User;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T062 (Ola 2a): {@code findAllByIds} contra un MongoDB real. Los ids de {@code users} son
 * ObjectId generados por Mongo; los que no tienen ese formato (por ejemplo, los de los fixtures)
 * no deben romper la consulta, solo no encontrar nada.
 */
@Testcontainers(disabledWithoutDocker = true)
class UserMongoAdapterIntegrationTest {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static MongoClient client;
    private static MongoTemplate mongoTemplate;
    private static UserMongoAdapter adapter;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getConnectionString());
        mongoTemplate = new MongoTemplate(client, "auth_it");
        adapter = new UserMongoAdapter(mongoTemplate);
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @BeforeEach
    void clean() {
        mongoTemplate.dropCollection(User.class);
    }

    @Test
    void findsUsersWhoseIdsWereGeneratedByMongo() {
        User caro = adapter.save(User.builder().username("caro.mz").email("caro@vito.com").build());
        User luis = adapter.save(User.builder().username("luis").email("luis@vito.com").build());
        adapter.save(User.builder().username("otro").email("otro@vito.com").build());

        assertTrue(ObjectId.isValid(caro.getId()), "se esperaba un ObjectId: " + caro.getId());
        assertEquals(Set.of("caro.mz", "luis"), usernames(adapter.findAllByIds(List.of(caro.getId(), luis.getId()))));
    }

    @Test
    void idsThatAreNotObjectIdsAreIgnored() {
        User caro = adapter.save(User.builder().username("caro.mz").email("caro@vito.com").build());

        List<User> found = adapter.findAllByIds(List.of("usr_01H8X2K4QV", caro.getId(), new ObjectId().toHexString()));

        assertEquals(Set.of("caro.mz"), usernames(found));
    }

    @Test
    void emptyListFindsNothing() {
        adapter.save(User.builder().username("caro.mz").email("caro@vito.com").build());

        assertTrue(adapter.findAllByIds(List.of()).isEmpty());
    }

    private static Set<String> usernames(List<User> users) {
        return users.stream().map(User::getUsername).collect(Collectors.toSet());
    }
}
