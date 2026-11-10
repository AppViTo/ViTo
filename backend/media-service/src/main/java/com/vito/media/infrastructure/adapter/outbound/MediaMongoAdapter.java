package com.vito.media.infrastructure.adapter.outbound;

import com.vito.media.domain.model.Media;
import com.vito.media.domain.port.outbound.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
    public List<Media> findByEventId(String eventId, int page, int size) {
        Query query = new Query(Criteria.where("eventId").is(eventId).and("deleted").is(false))
                .with(Sort.by(Sort.Direction.DESC, "uploadedAt").and(Sort.by(Sort.Direction.DESC, "_id")))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, Media.class);
    }

    @Override
    public List<Media> findByEventIdAndUrls(String eventId, List<String> urls) {
        Query query = new Query(Criteria.where("eventId").is(eventId)
                .and("location.url").in(urls)
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
