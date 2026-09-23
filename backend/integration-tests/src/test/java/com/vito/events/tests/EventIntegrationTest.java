package com.vito.events.tests;

import com.vito.tests.utils.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EventIntegrationTest extends BaseIntegrationTest {
    
    private static String organizerToken;
    private static String attendeeToken;
    private static String eventId;
    private static String invitationId;
    
    @BeforeAll
    static void setup() {
        String organizerUsername = "organizer_" + UUID.randomUUID().toString().substring(0, 8);
        String attendeeUsername = "attendee_" + UUID.randomUUID().toString().substring(0, 8);
        
        organizerToken = registerAndGetToken(organizerUsername, organizerUsername + "@test.com", "TestPassword123!");
        attendeeToken = registerAndGetToken(attendeeUsername, attendeeUsername + "@test.com", "TestPassword123!");
    }
    
    @Test
    @Order(1)
    void createEvent_Success() {
        Response response = authenticatedPost(organizerToken, EVENT_SERVICE_URL + "/api/v1/events", Map.of(
                "title", "Test Event " + UUID.randomUUID().toString().substring(0, 8),
                "description", "This is a test event",
                "date", LocalDateTime.now().plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "location", "Test Location",
                "maxAttendees", 100
        )).then()
                .statusCode(201)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
        eventId = response.jsonPath().getString("id");
    }
    
    @Test
    @Order(2)
    void getEvent_Success() {
        Response response = authenticatedGet(organizerToken, EVENT_SERVICE_URL + "/api/v1/events/" + eventId)
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
        assertEquals(eventId, response.jsonPath().getString("id"));
    }
    
    @Test
    @Order(3)
    void updateEvent_Success() {
        Response response = authenticatedPut(organizerToken, EVENT_SERVICE_URL + "/api/v1/events/" + eventId, Map.of(
                "title", "Updated Event Title",
                "description", "Updated description"
        )).then()
                .statusCode(200)
                .extract()
                .response();
        
        assertEquals("Updated Event Title", response.jsonPath().getString("title"));
    }
    
    @Test
    @Order(4)
    void sendInvitation_Success() {
        Response attendeeResponse = authenticatedGet(attendeeToken, AUTH_SERVICE_URL + "/api/v1/auth/me")
                .then().extract().response();
        String attendeeId = attendeeResponse.jsonPath().getString("id");
        
        Response response = authenticatedPost(organizerToken, 
                EVENT_SERVICE_URL + "/api/v1/events/" + eventId + "/invitations", Map.of(
                        "userId", attendeeId
                )).then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
        invitationId = response.jsonPath().getString("id");
    }
    
    @Test
    @Order(5)
    void respondToInvitation_Accept() {
        Response response = authenticatedPost(attendeeToken, 
                EVENT_SERVICE_URL + "/api/v1/invitations/" + invitationId + "/respond", Map.of(
                        "status", "ACCEPTED"
                )).then()
                .statusCode(200)
                .extract()
                .response();
        
        assertEquals("ACCEPTED", response.jsonPath().getString("status"));
    }
    
    @Test
    @Order(6)
    void respondRsvp_Maybe() {
        Response response = authenticatedPost(attendeeToken, 
                EVENT_SERVICE_URL + "/api/v1/events/" + eventId + "/rsvp", Map.of(
                        "status", "MAYBE"
                )).then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
    }
    
    @Test
    @Order(7)
    void getEventAttendees_Success() {
        Response response = authenticatedGet(organizerToken, 
                EVENT_SERVICE_URL + "/api/v1/events/" + eventId + "/attendees")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getList("content"));
    }
    
    @Test
    @Order(8)
    void deleteEvent_Success() {
        Response createResponse = authenticatedPost(organizerToken, EVENT_SERVICE_URL + "/api/v1/events", Map.of(
                "title", "Event to Delete",
                "date", LocalDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        )).then()
                .statusCode(201)
                .extract()
                .response();
        
        String deleteEventId = createResponse.jsonPath().getString("id");
        
        Response response = authenticatedDelete(organizerToken, 
                EVENT_SERVICE_URL + "/api/v1/events/" + deleteEventId)
                .then()
                .statusCode(204)
                .extract()
                .response();
    }
}
