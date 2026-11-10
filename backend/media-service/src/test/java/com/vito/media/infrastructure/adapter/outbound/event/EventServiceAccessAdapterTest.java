package com.vito.media.infrastructure.adapter.outbound.event;

import com.vito.media.domain.exception.MediaException;
import com.vito.media.domain.model.EventAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
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

/** T-B33: el acceso sale de la ruta INTERNO my-access de event-service, con el Bearer de la petición. */
class EventServiceAccessAdapterTest {

    private static final String BASE = "http://event-service:8083";

    private MockRestServiceServer server;
    private EventServiceAccessAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new EventServiceAccessAdapter(builder.build());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-guest");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void readsOrganizerAndRsvpForwardingTheBearer() {
        server.expect(requestTo(BASE + "/internal/v1/events/evt_1/my-access"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-guest"))
                .andRespond(withSuccess("{\"eventId\":\"evt_1\",\"organizerId\":\"usr_org\",\"rsvpStatus\":\"ACCEPTED\"}",
                        MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(new EventAccess("usr_org", "ACCEPTED")), adapter.findAccess("evt_1"));
        server.verify();
    }

    @Test
    void withoutRsvpTheStatusIsNull() {
        server.expect(requestTo(BASE + "/internal/v1/events/evt_1/my-access"))
                .andRespond(withSuccess("{\"eventId\":\"evt_1\",\"organizerId\":\"usr_org\"}", MediaType.APPLICATION_JSON));

        assertEquals(Optional.of(new EventAccess("usr_org", null)), adapter.findAccess("evt_1"));
    }

    @Test
    void notFoundMeansNoEvent() {
        server.expect(requestTo(BASE + "/internal/v1/events/evt_x/my-access")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals(Optional.empty(), adapter.findAccess("evt_x"));
    }

    @Test
    void unauthorizedIsNotAuthenticated() {
        server.expect(requestTo(BASE + "/internal/v1/events/evt_1/my-access")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(MediaException.NotAuthenticatedException.class, () -> adapter.findAccess("evt_1"));
    }

    @Test
    void serverErrorIsUnavailable() {
        server.expect(requestTo(BASE + "/internal/v1/events/evt_1/my-access"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(MediaException.DependencyUnavailableException.class, () -> adapter.findAccess("evt_1"));
    }
}
