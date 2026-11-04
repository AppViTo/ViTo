package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.InviteLink;
import com.vito.event.domain.port.outbound.InviteLinkRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/** T037 (R4): enlaces en {@code invite_links}; {@code uses} solo cambia con {@code $inc} condicional. */
@Component
@RequiredArgsConstructor
public class InviteLinkMongoAdapter implements InviteLinkRepository {

    static final String COLLECTION = "invite_links";

    private final MongoTemplate mongoTemplate;

    @Override
    public InviteLink save(InviteLink link) {
        return mongoTemplate.save(link, COLLECTION);
    }

    @Override
    public Optional<InviteLink> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, InviteLink.class, COLLECTION));
    }

    @Override
    public Optional<InviteLink> findByToken(String token) {
        return Optional.ofNullable(mongoTemplate.findOne(
                new Query(Criteria.where("token").is(token)), InviteLink.class, COLLECTION));
    }

    @Override
    public List<InviteLink> findByEventId(String eventId, Instant now, int page, int size) {
        // "Vigente" se calcula en la consulta para ordenar por él (FR-008)
        Document usable = new Document("$and", List.of(
                new Document("$eq", List.of("$revoked", false)),
                new Document("$gt", List.of("$expiresAt", Date.from(now))),
                new Document("$or", List.of(
                        // maxUses ausente o null = sin límite (List.of no admite null)
                        new Document("$eq", Arrays.asList(new Document("$ifNull", Arrays.asList("$maxUses", null)), null)),
                        new Document("$lt", List.of("$uses", "$maxUses"))))));
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("eventId").is(eventId)),
                Aggregation.stage(new Document("$addFields", new Document("usable", usable))),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "usable", "createdAt", "_id")),
                Aggregation.skip((long) page * size),
                Aggregation.limit(size),
                Aggregation.project().andExclude("usable"));
        return mongoTemplate.aggregate(aggregation, COLLECTION, InviteLink.class).getMappedResults();
    }

    @Override
    public boolean tryUse(String linkId, Instant now) {
        Query query = new Query(Criteria.where("_id").is(linkId)
                .and("revoked").is(false)
                .and("expiresAt").gt(now)
                .orOperator(Criteria.where("maxUses").is(null),
                        Criteria.expr(() -> new Document("$lt", List.of("$uses", "$maxUses")))));
        return mongoTemplate.updateFirst(query, new Update().inc("uses", 1), COLLECTION).getModifiedCount() == 1;
    }

    @Override
    public void releaseUse(String linkId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(linkId).and("uses").gt(0)),
                new Update().inc("uses", -1), COLLECTION);
    }

    @Override
    public void revoke(String linkId) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(linkId)),
                new Update().set("revoked", true), COLLECTION);
    }

    /** Token único (R4) y {@code {eventId, revoked, expiresAt}} para el listado (data-model). */
    public void ensureIndexes() {
        var indexOps = mongoTemplate.indexOps(COLLECTION);
        indexOps.createIndex(new Index().on("token", Sort.Direction.ASC).unique().named("token_unique"));
        indexOps.createIndex(new Index().on("eventId", Sort.Direction.ASC).on("revoked", Sort.Direction.ASC)
                .on("expiresAt", Sort.Direction.ASC).named("eventId_revoked_expiresAt"));
    }
}
