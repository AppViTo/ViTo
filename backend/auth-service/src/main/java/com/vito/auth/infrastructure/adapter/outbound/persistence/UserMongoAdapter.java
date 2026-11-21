package com.vito.auth.infrastructure.adapter.outbound.persistence;

import com.vito.auth.domain.model.User;
import com.vito.auth.domain.port.outbound.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationExpression;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.StringOperators;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class UserMongoAdapter implements UserRepository {
    
    private static final String SEARCH_RANK = "searchRank";
    private static final String REGEX_METACHARACTERS = "\\^$.|?*+()[]{}/";

    private final MongoTemplate mongoTemplate;

    @Override
    public Optional<User> findById(String id) {
        User user = mongoTemplate.findById(id, User.class);
        return Optional.ofNullable(user);
    }

    /** Spring convierte a ObjectId los ids con ese formato; los demás no encuentran nada. */
    @Override
    public List<User> findAllByIds(Collection<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Query query = new Query(org.springframework.data.mongodb.core.query.Criteria.where("id").in(ids));
        return mongoTemplate.find(query, User.class);
    }

    /**
     * Research R3: prefijos anclados y escapados (usan los índices de {@link #ensureSearchIndexes}
     * y {@code .*} no es comodín), rango 0/1/2 calculado en la base y paginación con
     * {@code $skip}/{@code $limit}. El orden es estable porque {@code username} es único.
     */
    @Override
    public List<User> search(String normalizedQuery, String excludeUserId, int page, int size) {
        String prefix = "^" + escapeRegex(normalizedQuery);

        List<Criteria> filters = new ArrayList<>();
        filters.add(new Criteria().orOperator(
                Criteria.where("search.username").regex(prefix),
                Criteria.where("search.terms").regex(prefix)));
        filters.add(Criteria.where("settings.privateProfile").ne(true));
        if (excludeUserId != null) {
            filters.add(Criteria.where("id").ne(excludeUserId));
        }

        AggregationExpression rank = ConditionalOperators
                .when(ComparisonOperators.valueOf("search.username").equalToValue(normalizedQuery)).then(0)
                .otherwise(ConditionalOperators
                        .when(StringOperators.valueOf("search.username").regexMatch(prefix)).then(1)
                        .otherwise(2));

        TypedAggregation<User> aggregation = Aggregation.newAggregation(User.class,
                Aggregation.match(new Criteria().andOperator(filters)),
                Aggregation.addFields().addFieldWithValue(SEARCH_RANK, rank).build(),
                Aggregation.sort(Sort.by(Sort.Order.asc(SEARCH_RANK), Sort.Order.asc("search.username"))),
                Aggregation.skip((long) page * size),
                Aggregation.limit(size));
        return mongoTemplate.aggregate(aggregation, User.class).getMappedResults();
    }

    /** Índices de la búsqueda (T028). Después de {@code Ola2aSearchMigration}; idempotente. */
    public void ensureSearchIndexes() {
        IndexOperations indexOps = mongoTemplate.indexOps(User.class);
        indexOps.createIndex(new Index().on("search.username", Sort.Direction.ASC).named("search_username"));
        indexOps.createIndex(new Index().on("search.terms", Sort.Direction.ASC).named("search_terms"));
    }

    /** El texto del usuario es literal: cualquier metacarácter de regex se escapa. */
    static String escapeRegex(String text) {
        StringBuilder escaped = new StringBuilder(text.length() + 8);
        for (char c : text.toCharArray()) {
            if (REGEX_METACHARACTERS.indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
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
