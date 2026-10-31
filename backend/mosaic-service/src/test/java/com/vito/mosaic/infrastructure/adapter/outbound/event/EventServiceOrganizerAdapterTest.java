package com.vito.mosaic.infrastructure.adapter.outbound.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** T-B26: el organizador sale de getEvent de event-service, con el Bearer de la petición en curso. */
class EventServiceOrganizerAdapterTest {

    private static final String BASE = "http://event-service:8083";

    private MockRestServiceServer server;
    private EventServiceOrganizerAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new EventServiceOrganizerAdapter(builder, BASE);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-org");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsOrganizerIdForwardingTheBearer() {
        server.expect(requestTo(BASE + "/api/v1/events/evt_1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-org"))
                .andRespond(withSuccess(
                        "{\"id\":\"evt_1\",\"organizerId\":\"usr_org\",\"title\":\"Los 30 de Marta\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals(Optional.of("usr_org"), adapter.findOrganizerId("evt_1"));
        server.verify();
    }

    @Test
    void notFoundMeansNoOrganizer() {
        server.expect(requestTo(BASE + "/api/v1/events/evt_x")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(Optional.empty(), adapter.findOrganizerId("evt_x"));
    }

    @Test
    void forbiddenMeansNoOrganizer() {
        server.expect(requestTo(BASE + "/api/v1/events/evt_p")).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertEquals(Optional.empty(), adapter.findOrganizerId("evt_p"));
    }

    @Test
    void eventWithoutOrganizerIdMeansNoOrganizer() {
        server.expect(requestTo(BASE + "/api/v1/events/evt_1"))
                .andRespond(withSuccess("{\"id\":\"evt_1\"}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.empty(), adapter.findOrganizerId("evt_1"));
    }

    @Test
    void serverErrorPropagates() {
        server.expect(requestTo(BASE + "/api/v1/events/evt_1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(HttpServerErrorException.class, () -> adapter.findOrganizerId("evt_1"));
    }
}
