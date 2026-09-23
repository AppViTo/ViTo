package com.vito.auth.domain.port.outbound;

import com.vito.auth.domain.model.User;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    
    Optional<User> findById(String id);
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    User save(User user);
    
    void deleteById(String id);
    
    List<User> findAll(Query query);
    
    long count();
}
