package com.vito.auth.infrastructure.migration;

import com.vito.auth.application.service.SearchNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 * T028 (Ola 2a, research R4, FR-007): llena {@code search.*} en los usuarios registrados antes de
 * la búsqueda, para que se puedan encontrar desde el despliegue. Mismo patrón que
 * {@code Ola1DataMigration} de {@code event-service}: una sola vez, registrada en
 * {@code migrations}; el paso es idempotente (solo toca a quien no tiene {@code search}) por si
 * una ejecución se corta; los logs solo llevan conteos, nunca datos de personas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Ola2aSearchMigration {

    static final String ID = "ola-2a-search";
    static final String MIGRATIONS = "migrations";
    private static final String USERS = "users";

    private final MongoTemplate mongoTemplate;

    /** @return {@code true} si se ejecutó; {@code false} si ya estaba registrada */
    public boolean run() {
        if (mongoTemplate.exists(new Query(Criteria.where("_id").is(ID)), MIGRATIONS)) {
            return false;
        }

        int usersUpdated = fillSearchFields();

        mongoTemplate.insert(new Document("_id", ID)
                .append("appliedAt", Date.from(Instant.now()))
                .append("usersUpdated", usersUpdated), MIGRATIONS);

        log.info("Migración {}: usuarios con campos de búsqueda={}", ID, usersUpdated);
        return true;
    }

    private int fillSearchFields() {
        Query query = new Query(Criteria.where("search").exists(false));
        query.fields().include("_id").include("username").include("profile.displayName");
        int updated = 0;
        for (Document user : mongoTemplate.find(query, Document.class, USERS)) {
            Document profile = user.get("profile", Document.class);
            String displayName = profile != null ? profile.getString("displayName") : null;
            Document search = new Document("username", SearchNormalizer.username(user.getString("username")))
                    .append("terms", SearchNormalizer.terms(displayName));
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(user.get("_id"))),
                    new Update().set("search", search), USERS);
            updated++;
        }
        return updated;
    }
}
