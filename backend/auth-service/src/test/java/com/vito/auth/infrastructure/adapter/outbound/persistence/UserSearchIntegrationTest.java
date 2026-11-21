package com.vito.auth.infrastructure.adapter.outbound.persistence;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.vito.auth.application.service.SearchNormalizer;
import com.vito.auth.domain.model.User;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T022 (Ola 2a, research R3): {@code searchUsers} contra un MongoDB real: prefijos sin tildes,
 * orden de FR-003, privacidad de FR-004, texto literal y paginación estable.
 */
@Testcontainers(disabledWithoutDocker = true)
class UserSearchIntegrationTest {

    @Container
    private static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static MongoClient client;
    private static MongoTemplate mongoTemplate;
    private static UserMongoAdapter adapter;

    private String searcherId;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MONGO.getConnectionString());
        mongoTemplate = new MongoTemplate(client, "auth_search_it");
        adapter = new UserMongoAdapter(mongoTemplate);
    }

    @AfterAll
    static void close() {
        client.close();
    }

    @BeforeEach
    void seed() {
        mongoTemplate.dropCollection(User.class);
        adapter.ensureSearchIndexes();
        searcherId = save("carolina", "Carolina Ruiz", false).getId();
    }

    @Test
    void accentlessQueryFindsAccentedName() {
        save("jperez", "José Pérez", false);

        assertEquals(List.of("jperez"), usernames(search("jose")));
        assertEquals(List.of("jperez"), usernames(search("pere")));
    }

    @Test
    void orderIsExactUsernameThenUsernamePrefixThenNamePrefix() {
        save("anabel", null, false);              // prefijo de username
        save("zoe", "Ana Zapata", false);         // prefijo de nombre
        save("ana", "Otra Persona", false);       // username exacto
        save("ana.b", null, false);               // prefijo de username
        save("beto", "Mariana Ana", false);       // prefijo de nombre (segunda palabra)

        assertEquals(List.of("ana", "ana.b", "anabel", "beto", "zoe"), usernames(search("ana")));
    }

    @Test
    void searcherIsExcluded() {
        save("caro.mz", "Caro Martínez", false);

        assertEquals(List.of("caro.mz"), usernames(search("caro")));
    }

    @Test
    void privateProfilesAreExcluded() {
        save("caro.privada", "Caro Privada", true);
        save("caro.publica", "Caro Pública", false);

        assertEquals(List.of("caro.publica"), usernames(search("caro")));
    }

    @Test
    void usersWithoutSettingsAreSearchable() {
        User legacy = User.builder().username("legado").email("legado@vito.com")
                .search(User.Search.builder().username("legado").terms(List.of()).build())
                .build();
        adapter.save(legacy);

        assertEquals(List.of("legado"), usernames(search("leg")));
    }

    @Test
    void neverMatchesByEmail() {
        adapter.save(User.builder().username("pepe").email("secreto.unico@vito.com")
                .search(User.Search.builder().username("pepe").terms(List.of()).build())
                .build());

        assertTrue(search("secreto").isEmpty());
        assertTrue(search("secreto.unico@vito.com").isEmpty());
    }

    @Test
    void regexCharactersAreLiteral() {
        save("ana", "Ana", false);
        save("a.b", null, false);

        assertTrue(search(".*").isEmpty());
        assertTrue(search("a.").stream().noneMatch(u -> u.getUsername().equals("ana")), "el punto no es comodín");
        assertEquals(List.of("a.b"), usernames(search("a.")));
        assertTrue(search("(ana").isEmpty());
    }

    @Test
    void prefixMatchesOnlyFromTheStart() {
        save("mariana", null, false);

        assertTrue(search("ana").isEmpty(), "\"ana\" no es prefijo de \"mariana\"");
    }

    @Test
    void pagesAreStableWithoutRepeatsNorGaps() {
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            String username = String.format("user%02d", i);
            save(username, "Nombre " + i, false);
            expected.add(username);
        }

        List<String> all = new ArrayList<>();
        all.addAll(usernames(adapter.search("user", searcherId, 0, 20)));
        all.addAll(usernames(adapter.search("user", searcherId, 1, 20)));
        all.addAll(usernames(adapter.search("user", searcherId, 2, 20)));

        assertEquals(expected, all);
        assertEquals(45, new HashSet<>(all).size());
        assertTrue(adapter.search("user", searcherId, 3, 20).isEmpty(), "página fuera de rango: vacía");
    }

    @Test
    void searchReturnsPublicFieldsOfTheUser() {
        User saved = save("jperez", "José Pérez", false);

        User found = search("jperez").get(0);

        assertEquals(saved.getId(), found.getId());
        assertEquals("José Pérez", found.getProfile().getDisplayName());
    }

    // ── helpers ─────────────────────────────────────────────────

    private List<User> search(String normalizedQuery) {
        return adapter.search(normalizedQuery, searcherId, 0, 20);
    }

    private static User save(String username, String displayName, boolean privateProfile) {
        return adapter.save(User.builder()
                .username(username)
                .email(username + "@vito.com")
                .profile(User.Profile.builder().displayName(displayName).build())
                .settings(User.Settings.builder().privateProfile(privateProfile).build())
                .search(User.Search.builder()
                        .username(SearchNormalizer.username(username))
                        .terms(SearchNormalizer.terms(displayName))
                        .build())
                .build());
    }

    private static List<String> usernames(List<User> users) {
        return users.stream().map(User::getUsername).toList();
    }
}
