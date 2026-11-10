package com.vito.social.infrastructure.adapter.outbound.persistence;

import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.domain.port.outbound.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public FollowRelationship save(FollowRelationship relationship) {
        return mongoTemplate.save(relationship, COLLECTION);
    }
    
    @Override
    public void delete(FollowRelationship relationship) {
        Query query = new Query(Criteria.where("followerId").is(relationship.getFollowerId())
                .and("followingId").is(relationship.getFollowingId()));
        mongoTemplate.remove(query, FollowRelationship.class, COLLECTION);
    }
    
    @Override
    public boolean existsByFollowerIdAndFollowingId(String followerId, String followingId) {
        Query query = new Query(Criteria.where("followerId").is(followerId)
                .and("followingId").is(followingId));
        return mongoTemplate.exists(query, FollowRelationship.class, COLLECTION);
    }
    
    @Override
    public List<FollowRelationship> findByFollowerId(String followerId, int page, int size) {
        Query query = new Query(Criteria.where("followerId").is(followerId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, FollowRelationship.class, COLLECTION);
    }
    
    @Override
    public List<FollowRelationship> findByFollowingId(String followingId, int page, int size) {
        Query query = new Query(Criteria.where("followingId").is(followingId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, FollowRelationship.class, COLLECTION);
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
    public List<String> findFollowerIdsByFollowingId(String followingId) {
        Query query = new Query(Criteria.where("followingId").is(followingId));
        List<FollowRelationship> relationships = mongoTemplate.find(query, FollowRelationship.class, COLLECTION);
        return relationships.stream()
                .map(FollowRelationship::getFollowerId)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> findFollowingIdsByFollowerId(String followerId) {
        Query query = new Query(Criteria.where("followerId").is(followerId));
        List<FollowRelationship> relationships = mongoTemplate.find(query, FollowRelationship.class, COLLECTION);
        return relationships.stream()
                .map(FollowRelationship::getFollowingId)
                .collect(Collectors.toList());
    }
}
