package com.vito.social.infrastructure.migration;

import com.vito.social.infrastructure.adapter.outbound.persistence.FollowMongoAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * T014 (Ola 2a, Principio IX): deja {@code follow_relationships} lista para el índice único de
 * T015. Mismo patrón que {@code Ola1DataMigration} de {@code event-service}: se ejecuta una sola
 * vez y se registra en {@code migrations}; cada paso es idempotente por si una ejecución se corta;
 * los logs solo llevan conteos, nunca identificadores de personas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Ola2aSocialMigration {

    static final String ID = "ola-2a-social";
    static final String MIGRATIONS = "migrations";
    private static final String COLLECTION = FollowMongoAdapter.COLLECTION;

    private final MongoTemplate mongoTemplate;

    /** @return {@code true} si se ejecutó; {@code false} si ya estaba registrada */
    public boolean run() {
        if (mongoTemplate.exists(new Query(Criteria.where("_id").is(ID)), MIGRATIONS)) {
            return false;
        }

        // Primero las fechas: el paso de duplicados las compara para quedarse con la más antigua
        int datesConverted = convertTextDates();
        long selfFollowsRemoved = removeSelfFollows();
        int duplicatesRemoved = removeDuplicates();

        mongoTemplate.insert(new Document("_id", ID)
                .append("appliedAt", Date.from(Instant.now()))
                .append("datesConverted", datesConverted)
                .append("selfFollowsRemoved", (int) selfFollowsRemoved)
                .append("duplicatesRemoved", duplicatesRemoved), MIGRATIONS);

        log.info("Migración {}: fechas convertidas={}, relaciones con uno mismo quitadas={}, duplicadas quitadas={}",
                ID, datesConverted, selfFollowsRemoved, duplicatesRemoved);
        return true;
    }

    /**
     * {@code createdAt} guardado como texto ({@code LocalDateTime} serializado, con la hora de
     * un contenedor en UTC) a {@code Date} UTC. Si no se puede leer, se quita el campo.
     */
    private int convertTextDates() {
        Query query = new Query(Criteria.where("createdAt").type(2)); // 2 = string
        query.fields().include("_id").include("createdAt");
        int converted = 0;
        for (Document doc : mongoTemplate.find(query, Document.class, COLLECTION)) {
            Date date = parse(doc.getString("createdAt"));
            Update update = date == null ? new Update().unset("createdAt") : new Update().set("createdAt", date);
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(doc.get("_id"))), update, COLLECTION);
            converted++;
        }
        return converted;
    }

    private static Date parse(String text) {
        try {
            return Date.from(Instant.parse(text));
        } catch (DateTimeParseException notInstant) {
            try {
                return Date.from(LocalDateTime.parse(text).toInstant(ZoneOffset.UTC));
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }
    }

    /** Nadie se sigue a sí mismo (FR de {@code followUser}: {@code 400} a uno mismo). */
    private long removeSelfFollows() {
        Query query = new Query(Criteria.expr(ComparisonOperators.Eq.valueOf("followerId").equalTo("followingId")));
        return mongoTemplate.remove(query, COLLECTION).getDeletedCount();
    }

    /**
     * Una sola relación por {@code {followerId, followingId}}: conserva la más antigua, porque
     * es desde cuándo lo sigue y define su lugar en las listas. Una sin fecha cuenta como la más
     * reciente, para no quedarse con ella si hay otra con fecha.
     */
    private int removeDuplicates() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("followerId", "followingId")
                        .push(new Document("id", "$_id").append("createdAt", "$createdAt")).as("relationships")
                        .count().as("count"),
                Aggregation.match(Criteria.where("count").gt(1)));
        Comparator<Document> oldestFirst = Comparator
                .comparing((Document rel) -> rel.get("createdAt") instanceof Date date ? date : null,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(rel -> String.valueOf(rel.get("id")));
        int removed = 0;
        for (Document group : mongoTemplate.aggregate(aggregation, COLLECTION, Document.class)) {
            List<Object> extra = group.getList("relationships", Document.class).stream()
                    .sorted(oldestFirst)
                    .skip(1)
                    .map(rel -> rel.get("id"))
                    .toList();
            removed += (int) mongoTemplate.remove(new Query(Criteria.where("_id").in(extra)), COLLECTION)
                    .getDeletedCount();
        }
        return removed;
    }
}
