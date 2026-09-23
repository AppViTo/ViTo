package com.vito.archive.infrastructure.adapter.outbound.persistence;

import com.vito.archive.domain.model.ArchiveComment;
import com.vito.archive.domain.port.outbound.ArchiveCommentRepository;
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
public class ArchiveCommentMongoAdapter implements ArchiveCommentRepository {
    
    private final MongoTemplate mongoTemplate;
    
    @Override
    public ArchiveComment save(ArchiveComment comment) {
        return mongoTemplate.save(comment, "archive_comments");
    }
    
    @Override
    public Optional<ArchiveComment> findById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        ArchiveComment comment = mongoTemplate.findOne(query, ArchiveComment.class, "archive_comments");
        return Optional.ofNullable(comment);
    }
    
    @Override
    public List<ArchiveComment> findByMediaId(String mediaId, int page, int size) {
        Query query = new Query(Criteria.where("mediaId").is(mediaId))
                .with(PageRequest.of(page, size))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ArchiveComment.class, "archive_comments");
    }
    
    @Override
    public List<ArchiveComment> findByArchiveId(String archiveId, int page, int size) {
        Query query = new Query(Criteria.where("archiveId").is(archiveId))
                .with(PageRequest.of(page, size))
                .skip((long) page * size)
                .limit(size);
        return mongoTemplate.find(query, ArchiveComment.class, "archive_comments");
    }
    
    @Override
    public long countByArchiveId(String archiveId) {
        Query query = new Query(Criteria.where("archiveId").is(archiveId));
        return mongoTemplate.count(query, ArchiveComment.class);
    }
    
    @Override
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, ArchiveComment.class, "archive_comments");
    }
    
    @Override
    public void deleteByArchiveId(String archiveId) {
        Query query = new Query(Criteria.where("archiveId").is(archiveId));
        mongoTemplate.remove(query, ArchiveComment.class, "archive_comments");
    }
}
