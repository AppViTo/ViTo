package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.domain.model.Media;
import com.vito.media.domain.port.outbound.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MediaMongoAdapter implements MediaRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public Media save(Media media) {
        return mongoTemplate.save(media);
    }

    @Override
    public Optional<Media> findById(String id) {
        Media media = mongoTemplate.findById(id, Media.class);
        return Optional.ofNullable(media);
    }

    @Override
    public List<Media> findByEventId(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("deleted").is(false));
        return mongoTemplate.find(query, Media.class);
    }

    @Override
    public List<Media> findByEventIdAndType(String eventId, Media.MediaType type) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("type").is(type)
                .and("deleted").is(false));
        return mongoTemplate.find(query, Media.class);
    }

    @Override
    public List<Media> findByUploadedBy(String userId) {
        Query query = new Query(Criteria.where("uploadedBy").is(userId)
                .and("deleted").is(false));
        return mongoTemplate.find(query, Media.class);
    }

    @Override
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, Media.class);
    }
}
