package com.vito.auth.domain.port.outbound;

import com.vito.auth.domain.model.User;
import org.springframework.data.mongodb.core.query.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(String id);

    /** Los usuarios que existan entre {@code ids}, en cualquier orden; los demás se ignoran. */
    List<User> findAllByIds(Collection<String> ids);

    /**
     * {@code searchUsers}: prefijo de {@code search.username} o de alguna de {@code search.terms},
     * sin perfiles privados ni {@code excludeUserId}. Orden: username exacto, prefijo de username,
     * prefijo de nombre; a igual rango, username ascendente. Paginado en la base.
     *
     * @param normalizedQuery texto ya normalizado ({@code SearchNormalizer.query}), literal
     * @param excludeUserId   quien busca; null si no hay que excluir a nadie
     */
    List<User> search(String normalizedQuery, String excludeUserId, int page, int size);

    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
    
    User save(User user);
    
    void deleteById(String id);
    
    List<User> findAll(Query query);
    
    long count();
}
