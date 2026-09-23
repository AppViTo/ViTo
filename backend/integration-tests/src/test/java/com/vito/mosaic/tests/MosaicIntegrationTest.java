package com.vito.mosaic.tests;

import com.vito.tests.utils.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MosaicIntegrationTest extends BaseIntegrationTest {
    
    private static String userToken;
    private static String eventId;
    private static String itemId;
    
    @BeforeAll
    static void setup() {
        String username = "mosaic_user_" + UUID.randomUUID().toString().substring(0, 8);
        userToken = registerAndGetToken(username, username + "@test.com", "TestPassword123!");
        
        Response eventResponse = authenticatedPost(userToken, EVENT_SERVICE_URL + "/api/v1/events", Map.of(
                "title", "Mosaic Test Event",
                "maxAttendees", 100
        )).then()
                .statusCode(201)
                .extract()
                .response();
        
        eventId = eventResponse.jsonPath().getString("id");
    }
    
    @Test
    @Order(1)
    void addItem_Success() {
        Response response = authenticatedPost(userToken, 
                MOSAIC_SERVICE_URL + "/api/v1/mosaic/" + eventId + "/items", Map.of(
                        "type", "CHECK_IN",
                        "content", "Just checked in!",
                        "userId", "user123"
                )).then()
                .statusCode(201)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
        itemId = response.jsonPath().getString("id");
    }
    
    @Test
    @Order(2)
    void getGrid_Success() {
        Response response = authenticatedGet(userToken, 
                MOSAIC_SERVICE_URL + "/api/v1/mosaic/" + eventId + "/grid")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
        assertNotNull(response.jsonPath().getList("items"));
    }
    
    @Test
    @Order(3)
    void getFeed_Success() {
        Response response = authenticatedGet(userToken, 
                MOSAIC_SERVICE_URL + "/api/v1/mosaic/" + eventId + "/feed?page=0&size=20")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getList("content"));
    }
    
    @Test
    @Order(4)
    void getItem_Success() {
        Response response = authenticatedGet(userToken, 
                MOSAIC_SERVICE_URL + "/api/v1/mosaic/items/" + itemId)
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertEquals(itemId, response.jsonPath().getString("id"));
    }
    
    @Test
    @Order(5)
    void likeItem_Success() {
        Response response = authenticatedPost(userToken, 
                MOSAIC_SERVICE_URL + "/api/v1/mosaic/items/" + itemId + "/like", null)
                .then()
                .statusCode(200)
                .extract()
                .response();
    }
    
    @Test
    @Order(6)
    void commentOnItem_Success() {
        Response response = authenticatedPost(userToken, 
                MOSAIC_SERVICE_URL + "/api/v1/mosaic/items/" + itemId + "/comments", Map.of(
                        "content", "Great post!"
                )).then()
                .statusCode(201)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
    }
    
    @Test
    @Order(7)
    void getItemComments_Success() {
        Response response = authenticatedGet(userToken, 
                MOSAIC_SERVICE_URL + "/api/v1/mosaic/items/" + itemId + "/comments")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getList("content"));
    }
    
    @Test
    @Order(8)
    void deleteItem_Success() {
        Response createResponse = authenticatedPost(userToken, 
                MOSAIC_SERVICE_URL + "/api/v1/mosaic/" + eventId + "/items", Map.of(
                        "type", "PHOTO",
                        "content", "Photo to delete",
                        "userId", "user123",
                        "mediaUrl", "http://example.com/photo.jpg"
                )).then()
                .statusCode(201)
                .extract()
                .response();
        
        String deleteItemId = createResponse.jsonPath().getString("id");
        
        Response response = authenticatedDelete(userToken, 
                MOSAIC_SERVICE_URL + "/api/v1/mosaic/items/" + deleteItemId)
                .then()
                .statusCode(204)
                .extract()
                .response();
    }
}
