package com.vito.auth.infrastructure.migration;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.vito.auth.domain.model.User;
import com.vito.auth.infrastructure.adapter.outbound.persistence.UserMongoAdapter;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T023 (Ola 2a, research R4, FR-007): los usuarios registrados antes de la ola quedan buscables
 * desde el despliegue. Una sola vez, registrada en {@code migrations}.
 */
@Testcontainers(disabledWithoutDocker = true)
class Ola2aSearchMigrationIntegrationTest {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static MongoClient client;
    private static MongoTemplate mongoTemplate;
    private static UserMongoAdapter adapter;
    private static Ola2aSearchMigration migration;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getConnectionString());
        mongoTemplate = new MongoTemplate(client, "auth_migration_it");
        adapter = new UserMongoAdapter(mongoTemplate);
        migration = new Ola2aSearchMigration(mongoTemplate);
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @BeforeEach
    void seed() {
        mongoTemplate.dropCollection(User.class);
        mongoTemplate.dropCollection(Ola2aSearchMigration.MIGRATIONS);
        // Como los dejó el registro antes de la Ola 2a: sin search.*
        mongoTemplate.insert(new Document("username", "JPerez").append("email", "j@vito.com")
                .append("profile", new Document("displayName", "José  Pérez")), "users");
        mongoTemplate.insert(new Document("username", "sin.nombre").append("email", "s@vito.com"), "users");
        mongoTemplate.insert(new Document("username", "perfil.vacio").append("email", "p@vito.com")
                .append("profile", new Document("bio", "sin nombre visible")), "users");
    }

    @Test
    void fillsSearchFieldsOfExistingUsers() {
        assertTrue(migration.run());

        User jose = byUsername("JPerez");
        assertEquals("jperez", jose.getSearch().getUsername());
        assertEquals(List.of("jose", "perez"), jose.getSearch().getTerms());

        User sinNombre = byUsername("sin.nombre");
        assertEquals("sin.nombre", sinNombre.getSearch().getUsername());
        assertEquals(List.of(), sinNombre.getSearch().getTerms());
        assertEquals(List.of(), byUsername("perfil.vacio").getSearch().getTerms());
    }

    @Test
    void migratedUsersAreFoundBySearch() {
        migration.run();
        adapter.ensureSearchIndexes();

        assertEquals(List.of("JPerez"), adapter.search("jose", null, 0, 20).stream().map(User::getUsername).toList());
    }

    @Test
    void runsOnceAndIsRegistered() {
        assertTrue(migration.run());
        Document registered = mongoTemplate.findById(Ola2aSearchMigration.ID, Document.class,
                Ola2aSearchMigration.MIGRATIONS);
        assertNotNull(registered);
        assertEquals(3, registered.getInteger("usersUpdated"));

        // Un usuario sin search.* después de la migración no lo toca una segunda ejecución
        mongoTemplate.insert(new Document("username", "tarde").append("email", "t@vito.com"), "users");
        assertFalse(migration.run());
        assertEquals(null, byUsername("tarde").getSearch());
    }

    @Test
    void usersThatAlreadyHaveSearchFieldsAreLeftAlone() {
        mongoTemplate.insert(new Document("username", "nuevo").append("email", "n@vito.com")
                .append("search", new Document("username", "valor-propio").append("terms", List.of("x"))), "users");

        migration.run();

        assertEquals("valor-propio", byUsername("nuevo").getSearch().getUsername());
    }

    @Test
    void indexesCanBeCreatedTwice() {
        migration.run();
        adapter.ensureSearchIndexes();
        adapter.ensureSearchIndexes();

        List<String> indexed = mongoTemplate.indexOps(User.class).getIndexInfo().stream()
                .flatMap(info -> info.getIndexFields().stream())
                .map(field -> field.getKey())
                .toList();
        assertTrue(indexed.contains("search.username"), indexed.toString());
        assertTrue(indexed.contains("search.terms"), indexed.toString());
    }

    private static User byUsername(String username) {
        return mongoTemplate.findOne(new Query(Criteria.where("username").is(username)), User.class);
    }
}
