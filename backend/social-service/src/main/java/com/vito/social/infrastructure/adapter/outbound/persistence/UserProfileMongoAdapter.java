package com.vito.social.infrastructure.adapter.outbound.persistence;

import com.vito.social.domain.model.SocialProfile;
import com.vito.social.domain.port.outbound.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserProfileMongoAdapter implements UserProfileRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public Optional<SocialProfile> findByUserId(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        SocialProfile profile = mongoTemplate.findOne(query, SocialProfile.class, "user_profiles");
        return Optional.ofNullable(profile);
    }
    
    @Override
    public List<SocialProfile> findByUserIds(List<String> userIds) {
        Query query = new Query(Criteria.where("userId").in(userIds));
        return mongoTemplate.find(query, SocialProfile.class, "user_profiles");
    }
    
    @Override
    public SocialProfile save(SocialProfile profile) {
        return mongoTemplate.save(profile, "user_profiles");
    }
    
    @Override
    public void incrementFollowersCount(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update().inc("followersCount", 1);
        mongoTemplate.updateFirst(query, update, SocialProfile.class, "user_profiles");
    }
    
    @Override
    public void decrementFollowersCount(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update().inc("followersCount", -1);
        mongoTemplate.updateFirst(query, update, SocialProfile.class, "user_profiles");
    }
    
    @Override
    public void incrementFollowingCount(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update().inc("followingCount", 1);
        mongoTemplate.updateFirst(query, update, SocialProfile.class, "user_profiles");
    }
    
    @Override
    public void decrementFollowingCount(String userId) {
        Query query = new Query(Criteria.where("userId").is(userId));
        Update update = new Update().inc("followingCount", -1);
        mongoTemplate.updateFirst(query, update, SocialProfile.class, "user_profiles");
    }
}
