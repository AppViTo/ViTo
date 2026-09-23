package com.vito.mosaic.domain.port.outbound;

import com.vito.mosaic.domain.model.MosaicGrid;

import java.util.Optional;

public interface MosaicGridRepository {
    
    MosaicGrid save(MosaicGrid grid);
    
    Optional<MosaicGrid> findByEventId(String eventId);
    
    void delete(String eventId);
}
