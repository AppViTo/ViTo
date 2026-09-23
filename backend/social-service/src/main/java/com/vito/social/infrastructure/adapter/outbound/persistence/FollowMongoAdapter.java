package com.vito.social.infrastructure.adapter.outbound.persistence;

import com.vito.social.domain.model.FollowRelationship;
import com.vito.social.domain.port.outbound.FollowRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
public class FollowMongoAdapter implements FollowRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public FollowRelationship save(FollowRelationship relationship) {
        return mongoTemplate.save(relationship, "follow_relationships");
    }
    
    @Override
    public void delete(FollowRelationship relationship) {
        Query query = new Query(Criteria.where("followerId").is(relationship.getFollowerId())
                .and("followingId").is(relationship.getFollowingId()));
        mongoTemplate.remove(query, FollowRelationship.class, "follow_relationships");
    }
    
    @Override
    public boolean existsByFollowerIdAndFollowingId(String followerId, String followingId) {
        Query query = new Query(Criteria.where("followerId").is(followerId)
                .and("followingId").is(followingId));
        return mongoTemplate.exists(query, FollowRelationship.class, "follow_relationships");
    }
    
    @Override
    public List<FollowRelationship> findByFollowerId(String followerId, int page, int size) {
        Query query = new Query(Criteria.where("followerId").is(followerId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, FollowRelationship.class, "follow_relationships");
    }
    
    @Override
    public List<FollowRelationship> findByFollowingId(String followingId, int page, int size) {
        Query query = new Query(Criteria.where("followingId").is(followingId))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, FollowRelationship.class, "follow_relationships");
    }
    
    @Override
    public long countByFollowerId(String followerId) {
        Query query = new Query(Criteria.where("followerId").is(followerId));
        return mongoTemplate.count(query, FollowRelationship.class, "follow_relationships");
    }
    
    @Override
    public long countByFollowingId(String followingId) {
        Query query = new Query(Criteria.where("followingId").is(followingId));
        return mongoTemplate.count(query, FollowRelationship.class, "follow_relationships");
    }
    
    @Override
    public List<String> findFollowerIdsByFollowingId(String followingId) {
        Query query = new Query(Criteria.where("followingId").is(followingId));
        List<FollowRelationship> relationships = mongoTemplate.find(query, FollowRelationship.class, "follow_relationships");
        return relationships.stream()
                .map(FollowRelationship::getFollowerId)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> findFollowingIdsByFollowerId(String followerId) {
        Query query = new Query(Criteria.where("followerId").is(followerId));
        List<FollowRelationship> relationships = mongoTemplate.find(query, FollowRelationship.class, "follow_relationships");
        return relationships.stream()
                .map(FollowRelationship::getFollowingId)
                .collect(Collectors.toList());
    }
}
