package com.vito.mosaic.infrastructure.adapter.outbound.persistence;

import com.vito.mosaic.domain.model.MosaicFeedItem;
import com.vito.mosaic.domain.port.outbound.MosaicFeedRepository;
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
public class MosaicFeedMongoAdapter implements MosaicFeedRepository {
    
    /** Borrado lógico de T-B26; {@code ne(true)} incluye los documentos anteriores sin el campo. */
    private static final String REMOVED = "removed";

    private final MongoTemplate mongoTemplate;
    
    @Override
    public MosaicFeedItem save(MosaicFeedItem item) {
        return mongoTemplate.save(item, "mosaic_feed");
    }
    
    @Override
    public Optional<MosaicFeedItem> findById(String itemId) {
        Query query = new Query(Criteria.where("id").is(itemId));
        MosaicFeedItem item = mongoTemplate.findOne(query, MosaicFeedItem.class, "mosaic_feed");
        return Optional.ofNullable(item);
    }
    
    @Override
    public List<MosaicFeedItem> findByEventId(String eventId, int page, int size) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and(REMOVED).ne(true))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "timestamp"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, MosaicFeedItem.class, "mosaic_feed");
    }
    
    @Override
    public List<MosaicFeedItem> findByEventIdAndType(String eventId, MosaicFeedItem.FeedItemType type, int page, int size) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("type").is(type)
                .and(REMOVED).ne(true))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "timestamp"))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, MosaicFeedItem.class, "mosaic_feed");
    }
    
    @Override
    public long countByEventId(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and(REMOVED).ne(true));
        return mongoTemplate.count(query, MosaicFeedItem.class, "mosaic_feed");
    }
    
    @Override
    public void delete(String itemId) {
        Query query = new Query(Criteria.where("id").is(itemId));
        mongoTemplate.remove(query, MosaicFeedItem.class, "mosaic_feed");
    }
    
    @Override
    public void like(String itemId, String userId) {
        Query query = new Query(Criteria.where("id").is(itemId)
                .and("likedBy").ne(userId));
        Update update = new Update()
                .addToSet("likedBy", userId)
                .inc("likesCount", 1);
        mongoTemplate.updateFirst(query, update, MosaicFeedItem.class, "mosaic_feed");
    }
    
    @Override
    public void unlike(String itemId, String userId) {
        Query query = new Query(Criteria.where("id").is(itemId)
                .and("likedBy").is(userId));
        Update update = new Update()
                .pull("likedBy", userId)
                .inc("likesCount", -1);
        mongoTemplate.updateFirst(query, update, MosaicFeedItem.class, "mosaic_feed");
    }
}
