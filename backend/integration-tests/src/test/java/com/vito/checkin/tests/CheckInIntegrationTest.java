package com.vito.checkin.tests;

import com.vito.tests.utils.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CheckInIntegrationTest extends BaseIntegrationTest {
    
    private static String organizerToken;
    private static String attendeeToken;
    private static String eventId;
    private static String ticketId;
    private static String qrCode;
    
    @BeforeAll
    static void setup() {
        String organizerUsername = "checkin_organizer_" + UUID.randomUUID().toString().substring(0, 8);
        String attendeeUsername = "checkin_attendee_" + UUID.randomUUID().toString().substring(0, 8);
        
        organizerToken = registerAndGetToken(organizerUsername, organizerUsername + "@test.com", "TestPassword123!");
        attendeeToken = registerAndGetToken(attendeeUsername, attendeeUsername + "@test.com", "TestPassword123!");
        
        Response eventResponse = authenticatedPost(organizerToken, EVENT_SERVICE_URL + "/api/v1/events", Map.of(
                "title", "CheckIn Test Event",
                "maxAttendees", 50
        )).then()
                .statusCode(201)
                .extract()
                .response();
        
        eventId = eventResponse.jsonPath().getString("id");
    }
    
    @Test
    @Order(1)
    void generateTicket_Success() {
        Response response = authenticatedPost(attendeeToken, 
                CHECKIN_SERVICE_URL + "/api/v1/checkin/tickets", Map.of(
                        "eventId", eventId
                )).then()
                .statusCode(201)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
        assertNotNull(response.jsonPath().getString("qrCode"));
        ticketId = response.jsonPath().getString("id");
        qrCode = response.jsonPath().getString("qrCode");
    }
    
    @Test
    @Order(2)
    void getTicket_Success() {
        Response response = authenticatedGet(attendeeToken, 
                CHECKIN_SERVICE_URL + "/api/v1/checkin/tickets/" + ticketId)
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertEquals(ticketId, response.jsonPath().getString("id"));
        assertEquals(eventId, response.jsonPath().getString("eventId"));
    }
    
    @Test
    @Order(3)
    void validateCheckIn_Valid() {
        Response response = authenticatedPost(organizerToken, 
                CHECKIN_SERVICE_URL + "/api/v1/checkin/validate", Map.of(
                        "ticketId", ticketId,
                        "qrCode", qrCode
                )).then()
                .statusCode(200)
                .extract()
                .response();
        
        assertTrue(response.jsonPath().getBoolean("valid"));
    }
    
    @Test
    @Order(4)
    void checkIn_Success() {
        Response response = authenticatedPost(organizerToken, 
                CHECKIN_SERVICE_URL + "/api/v1/checkin", Map.of(
                        "ticketId", ticketId,
                        "qrCode", qrCode
                )).then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
        assertEquals("USED", response.jsonPath().getString("status"));
    }
    
    @Test
    @Order(5)
    void validateCheckIn_AlreadyUsed() {
        Response response = authenticatedPost(organizerToken, 
                CHECKIN_SERVICE_URL + "/api/v1/checkin/validate", Map.of(
                        "ticketId", ticketId,
                        "qrCode", qrCode
                )).then()
                .statusCode(400)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("message"));
    }
    
    @Test
    @Order(6)
    void getEventCheckIns_Success() {
        Response response = authenticatedGet(organizerToken, 
                CHECKIN_SERVICE_URL + "/api/v1/checkin/events/" + eventId + "/checkins")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getList("content"));
    }
    
    @Test
    @Order(7)
    void getEventStats_Success() {
        Response response = authenticatedGet(organizerToken, 
                CHECKIN_SERVICE_URL + "/api/v1/checkin/events/" + eventId + "/stats")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().get("totalTickets"));
        assertNotNull(response.jsonPath().get("totalCheckedIn"));
    }
    
    @Test
    @Order(8)
    void getUserTickets_Success() {
        Response response = authenticatedGet(attendeeToken, 
                CHECKIN_SERVICE_URL + "/api/v1/checkin/my-tickets")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getList("content"));
    }
}
