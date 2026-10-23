package com.vito.event.infrastructure.adapter.outbound.persistence;

import com.vito.event.domain.model.Event;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** T-B17: {@code /events/upcoming} no lista eventos privados. */
@ExtendWith(MockitoExtension.class)
class EventMongoAdapterTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private EventMongoAdapter adapter;

    @Test
    void findUpcomingEvents_ExcludesPrivateEvents() {
        Document filter = mappedFilter(upcomingQuery(0, 20));

        // Un evento privado, publicado y futuro de otro organizador cumple status y fecha: lo excluye isPublic
        assertEquals(Event.EventStatus.PUBLISHED.name(), filter.get("status"));
        assertTrue(filter.containsKey("details.startDate"));
        assertEquals(Boolean.TRUE, filter.get("settings.isPublic"));
    }

    @Test
    void settingsIsPublic_IsPersistedAsIsPublic() {
        // QueryMapper deja intacta una clave que no reconoce: se comprueba el nombre en Mongo aparte
        String fieldName = mappingContext()
                .getRequiredPersistentEntity(Event.EventSettings.class)
                .getRequiredPersistentProperty("isPublic")
                .getFieldName();

        assertEquals("isPublic", fieldName);
    }

    @Test
    void findUpcomingEvents_PagesWithSkipAndLimit() {
        Query query = upcomingQuery(3, 25);

        assertEquals(75, query.getSkip());
        assertEquals(25, query.getLimit());
    }

    private Query upcomingQuery(int page, int size) {
        adapter.findUpcomingEvents(page, size);
        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Event.class), any(String.class));
        return captor.getValue();
    }

    /** Filtro tal como llega a Mongo: valida que {@code settings.isPublic} es el nombre persistido. */
    private static Document mappedFilter(Query query) {
        MongoMappingContext context = mappingContext();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(new MongoCustomConversions(List.of()));
        converter.afterPropertiesSet();
        return new QueryMapper(converter)
                .getMappedObject(query.getQueryObject(), context.getPersistentEntity(Event.class));
    }

    /** Como lo configura Spring Boot: los tipos simples de Mongo (fechas, etc.) no son entidades. */
    private static MongoMappingContext mappingContext() {
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        context.afterPropertiesSet();
        return context;
    }
}
