package com.vito.mosaic.infrastructure.adapter.outbound.persistence;

import com.vito.mosaic.domain.model.MosaicGrid;
import com.vito.mosaic.domain.port.outbound.MosaicGridRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.FindAndReplaceOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
public class MosaicGridMongoAdapter implements MosaicGridRepository {
    
    private final MongoTemplate mongoTemplate;
    
    /**
     * Una grilla por evento. {@link MosaicGrid} no tiene {@code @Id}, así que {@code save()} insertaba
     * un documento nuevo en cada cambio y {@link #findByEventId} seguía leyendo el primero: la celda
     * retirada (T-B26) volvía a aparecer. Se reemplaza el documento del evento (upsert).
     */
    @Override
    public MosaicGrid save(MosaicGrid grid) {
        Query query = new Query(Criteria.where("eventId").is(grid.getEventId()));
        MosaicGrid saved = mongoTemplate.findAndReplace(query, grid,
                FindAndReplaceOptions.options().upsert().returnNew(), MosaicGrid.class, "mosaic_grids");
        return saved != null ? saved : grid;
    }
    
    @Override
    public Optional<MosaicGrid> findByEventId(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        MosaicGrid grid = mongoTemplate.findOne(query, MosaicGrid.class, "mosaic_grids");
        return Optional.ofNullable(grid);
    }
    
    @Override
    public void delete(String eventId) {
        Query query = new Query(Criteria.where("eventId").is(eventId));
        mongoTemplate.remove(query, MosaicGrid.class, "mosaic_grids");
    }
}
