package com.vito.archive.infrastructure.adapter.outbound.persistence;

import com.vito.archive.domain.model.ArchiveMedia;
import com.vito.archive.domain.port.outbound.ArchiveMediaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ArchiveMediaMongoAdapter implements ArchiveMediaRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public ArchiveMedia save(ArchiveMedia media) {
        return mongoTemplate.save(media, "archive_media");
    }
    
    @Override
    public Optional<ArchiveMedia> findById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        ArchiveMedia media = mongoTemplate.findOne(query, ArchiveMedia.class, "archive_media");
        return Optional.ofNullable(media);
    }
    
    @Override
    public List<ArchiveMedia> findByArchiveId(String archiveId, int page, int size) {
        Query query = new Query(Criteria.where("archiveId").is(archiveId))
                .with(PageRequest.of(page, size))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ArchiveMedia.class, "archive_media");
    }
    
    @Override
    public List<ArchiveMedia> findByEventId(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        return mongoTemplate.find(query, ArchiveMedia.class, "archive_media");
    }
    
    @Override
    public long countByArchiveId(String archiveId) {
        Query query = new Query(Criteria.where("archiveId").is(archiveId));
        return mongoTemplate.count(query, ArchiveMedia.class);
    }
    
    @Override
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, ArchiveMedia.class, "archive_media");
    }
    
    @Override
    public void deleteByArchiveId(String archiveId) {
        Query query = new Query(Criteria.where("archiveId").is(archiveId));
        mongoTemplate.remove(query, ArchiveMedia.class, "archive_media");
    }
}
