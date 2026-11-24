package com.vito.social.infrastructure.adapter.outbound.persistence;

import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.domain.port.outbound.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FollowMongoAdapter implements FollowRepository {

    public static final String COLLECTION = "follow_relationships";
    public static final String UNIQUE_PAIR_INDEX = "followerId_followingId_unique";

    private final MongoTemplate mongoTemplate;

    /**
     * T015 (Ola 2a, R5 y R7): una relación por par, que hace idempotente el follow, y los índices
     * de las listas paginadas por fecha y de sus contadores. Se crean después de
     * {@code Ola2aSocialMigration}, que quita los duplicados que impedirían el índice único.
     */
    public void ensureIndexes() {
        var indexOps = mongoTemplate.indexOps(COLLECTION);
        indexOps.createIndex(new Index().on("followerId", Sort.Direction.ASC).on("followingId", Sort.Direction.ASC)
                .unique().named(UNIQUE_PAIR_INDEX));
        indexOps.createIndex(new Index().on("followingId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC).named("followingId_createdAt_id"));
        indexOps.createIndex(new Index().on("followerId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC).named("followerId_createdAt_id"));
    }
    
    @Override
    public boolean insertIfAbsent(FollowRelationship relationship) {
        try {
            mongoTemplate.insert(relationship, COLLECTION);
            return true;
        } catch (DuplicateKeyException alreadyFollowing) {
            // El índice único del par rechazó el segundo insert: ya lo seguía (research R5)
            return false;
        }
    }
    
    @Override
    public boolean deleteIfPresent(String followerId, String followingId) {
        Query query = new Query(Criteria.where("followerId").is(followerId)
                .and("followingId").is(followingId));
        return mongoTemplate.remove(query, FollowRelationship.class, COLLECTION).getDeletedCount() > 0;
    }
    
    @Override
    public boolean existsByFollowerIdAndFollowingId(String followerId, String followingId) {
        Query query = new Query(Criteria.where("followerId").is(followerId)
                .and("followingId").is(followingId));
        return mongoTemplate.exists(query, FollowRelationship.class, COLLECTION);
    }
    
    @Override
    public long countByFollowerId(String followerId) {
        Query query = new Query(Criteria.where("followerId").is(followerId));
        return mongoTemplate.count(query, FollowRelationship.class, COLLECTION);
    }
    
    @Override
    public long countByFollowingId(String followingId) {
        Query query = new Query(Criteria.where("followingId").is(followingId));
        return mongoTemplate.count(query, FollowRelationship.class, COLLECTION);
    }
    
    @Override
    public List<String> findFollowerIds(String userId, int page, int size) {
        return page("followingId", userId, page, size).stream()
                .map(FollowRelationship::getFollowerId)
                .toList();
    }

    @Override
    public List<String> findFollowingIds(String userId, int page, int size) {
        return page("followerId", userId, page, size).stream()
                .map(FollowRelationship::getFollowingId)
                .toList();
    }

    /**
     * Una página de las relaciones en las que {@code userId} está en {@code side}, con el orden de
     * los índices {@code *_createdAt_id} (R7): {@code _id} desempata las fechas iguales, así que
     * ninguna fila sale en dos páginas.
     */
    private List<FollowRelationship> page(String side, String userId, int page, int size) {
        Query query = new Query(Criteria.where(side).is(userId))
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, FollowRelationship.class, COLLECTION);
    }
}
