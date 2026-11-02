package com.vito.event.infrastructure.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationUpdate;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * T066 (Principio IX): deja los datos listos para la Ola 1 antes de crear los índices únicos (T021).
 * Se ejecuta una sola vez: al terminar se registra en la colección {@code migrations}. Cada paso
 * es idempotente por sí mismo, por si una ejecución se corta a la mitad. Los logs solo llevan
 * conteos, nunca identificadores de personas.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Ola1DataMigration {

    static final String ID = "ola-1-data";
    static final String MIGRATIONS = "migrations";

    private final MongoTemplate mongoTemplate;

    /** @return {@code true} si se ejecutó; {@code false} si ya estaba registrada */
    public boolean run() {
        if (mongoTemplate.exists(new Query(Criteria.where("_id").is(ID)), MIGRATIONS)) {
            return false;
        }

        int datesConverted = convertTextDates("rsvps", "respondedAt", "waitlistedAt", "checkedInAt")
                + convertTextDates("invitations", "sentAt", "expiresAt", "respondedAt");
        int rsvpsRemoved = removeDuplicates("rsvps", "userId");
        int invitationsRemoved = removeDuplicates("invitations", "inviteeId");
        long waitlistedFilled = fillWaitlistedAt();
        int eventsRecalculated = recalculateStats();

        mongoTemplate.insert(new Document("_id", ID)
                .append("appliedAt", Date.from(Instant.now()))
                .append("datesConverted", datesConverted)
                .append("rsvpsRemoved", rsvpsRemoved)
                .append("invitationsRemoved", invitationsRemoved)
                .append("waitlistedAtFilled", waitlistedFilled)
                .append("eventsRecalculated", eventsRecalculated), MIGRATIONS);

        log.info("Migración {}: fechas convertidas={}, RSVP duplicados quitados={}, invitaciones duplicadas quitadas={}, "
                        + "waitlistedAt completados={}, eventos recalculados={}",
                ID, datesConverted, rsvpsRemoved, invitationsRemoved, waitlistedFilled, eventsRecalculated);
        return true;
    }

    /**
     * (4) Fechas guardadas como texto con hora local ({@code LocalDateTime} serializado) a
     * {@code Date} UTC. Los contenedores corren en UTC, así que esa hora local es UTC.
     */
    private int convertTextDates(String collection, String... fields) {
        int converted = 0;
        for (String field : fields) {
            Query query = new Query(Criteria.where(field).type(2)); // 2 = string
            query.fields().include("_id").include(field);
            for (Document doc : mongoTemplate.find(query, Document.class, collection)) {
                Date date = parse(doc.getString(field));
                Update update = date == null ? new Update().unset(field) : new Update().set(field, date);
                mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(doc.get("_id"))), update, collection);
                converted++;
            }
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

    /**
     * (1) Un solo documento por {@code {eventId, <persona>}}: conserva el más reciente (por
     * {@code respondedAt}, luego {@code sentAt}, luego el {@code _id}) y borra el resto.
     */
    private int removeDuplicates(String collection, String personField) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "respondedAt", "sentAt", "_id")),
                Aggregation.group("eventId", personField).push("_id").as("ids").count().as("count"),
                Aggregation.match(Criteria.where("count").gt(1)));
        int removed = 0;
        for (Document group : mongoTemplate.aggregate(aggregation, collection, Document.class)) {
            List<?> ids = group.getList("ids", Object.class);
            List<?> extra = ids.subList(1, ids.size());
            removed += (int) mongoTemplate.remove(new Query(Criteria.where("_id").in(extra)), collection)
                    .getDeletedCount();
        }
        return removed;
    }

    /** (3) {@code waitlistedAt} de quien ya estaba en lista de espera: su última respuesta. */
    private long fillWaitlistedAt() {
        Query query = new Query(Criteria.where("status").is("WAITLISTED").and("waitlistedAt").exists(false));
        AggregationUpdate update = AggregationUpdate.update().set("waitlistedAt").toValueOf(Fields.field("respondedAt"));
        return mongoTemplate.updateMulti(query, update, "rsvps").getModifiedCount();
    }

    /**
     * (2) Recalcula los contadores de cada evento desde {@code rsvps} e {@code invitations}:
     * los anteriores se escribían leyendo y reescribiendo el documento y pudieron perder cambios.
     */
    private int recalculateStats() {
        Map<String, Map<String, Integer>> rsvpCounts = new HashMap<>();
        Aggregation byStatus = Aggregation.newAggregation(
                Aggregation.group("eventId", "status").count().as("count"));
        for (Document row : mongoTemplate.aggregate(byStatus, "rsvps", Document.class)) {
            Document id = row.get("_id", Document.class);
            rsvpCounts.computeIfAbsent(String.valueOf(id.get("eventId")), k -> new HashMap<>())
                    .put(String.valueOf(id.get("status")), row.getInteger("count"));
        }
        Map<String, Integer> invited = new HashMap<>();
        for (Document row : mongoTemplate.aggregate(
                Aggregation.newAggregation(Aggregation.group("eventId").count().as("count")),
                "invitations", Document.class)) {
            invited.put(String.valueOf(row.get("_id")), row.getInteger("count"));
        }

        int recalculated = 0;
        Query allEvents = new Query();
        allEvents.fields().include("_id");
        for (Document event : mongoTemplate.find(allEvents, Document.class, "events")) {
            Object rawId = event.get("_id");
            String eventId = rawId instanceof ObjectId objectId ? objectId.toHexString() : String.valueOf(rawId);
            Map<String, Integer> counts = rsvpCounts.getOrDefault(eventId, Map.of());
            Update update = new Update()
                    .set("stats.totalAccepted", counts.getOrDefault("ACCEPTED", 0))
                    .set("stats.totalDeclined", counts.getOrDefault("DECLINED", 0))
                    .set("stats.totalMaybe", counts.getOrDefault("MAYBE", 0))
                    .set("stats.totalWaitlisted", counts.getOrDefault("WAITLISTED", 0))
                    .set("stats.totalInvited", invited.getOrDefault(eventId, 0));
            mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(rawId)), update, "events");
            recalculated++;
        }
        return recalculated;
    }
}
