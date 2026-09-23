package com.vito.auth.infrastructure.adapter.outbound.persistence;

import com.vito.auth.domain.model.User;
import com.vito.auth.domain.port.outbound.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserMongoAdapter implements UserRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public Optional<User> findById(String id) {
        User user = mongoTemplate.findById(id, User.class);
        return Optional.ofNullable(user);
    }
    
    @Override
    public Optional<User> findByUsername(String username) {
        Query query = new Query(org.springframework.data.mongodb.core.query.Criteria.where("username").is(username));
        User user = mongoTemplate.findOne(query, User.class);
        return Optional.ofNullable(user);
    }
    
    @Override
    public Optional<User> findByEmail(String email) {
        Query query = new Query(org.springframework.data.mongodb.core.query.Criteria.where("email").is(email));
        User user = mongoTemplate.findOne(query, User.class);
        return Optional.ofNullable(user);
    }
    
    @Override
    public boolean existsByUsername(String username) {
        Query query = new Query(org.springframework.data.mongodb.core.query.Criteria.where("username").is(username));
        return mongoTemplate.exists(query, User.class);
    }
    
    @Override
    public boolean existsByEmail(String email) {
        Query query = new Query(org.springframework.data.mongodb.core.query.Criteria.where("email").is(email));
        return mongoTemplate.exists(query, User.class);
    }
    
    @Override
    public User save(User user) {
        return mongoTemplate.save(user);
    }
    
    @Override
    public void deleteById(String id) {
        Query query = new Query(org.springframework.data.mongodb.core.query.Criteria.where("_id").is(id));
        mongoTemplate.remove(query, User.class);
    }
    
    @Override
    public List<User> findAll(Query query) {
        return mongoTemplate.find(query, User.class);
    }
    
    @Override
    public long count() {
        return mongoTemplate.count(new Query(), User.class);
    }
}
