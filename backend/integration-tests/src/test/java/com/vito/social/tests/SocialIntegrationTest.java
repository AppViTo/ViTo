package com.vito.social.tests;

import com.vito.tests.utils.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SocialIntegrationTest extends BaseIntegrationTest {
    
    private static String user1Token;
    private static String user2Token;
    private static String user1Id;
    private static String user2Id;
    
    @BeforeAll
    static void setup() {
        String username1 = "socialuser1_" + UUID.randomUUID().toString().substring(0, 8);
        String username2 = "socialuser2_" + UUID.randomUUID().toString().substring(0, 8);
        
        user1Token = registerAndGetToken(username1, username1 + "@test.com", "TestPassword123!");
        user2Token = registerAndGetToken(username2, username2 + "@test.com", "TestPassword123!");
        
        Response user1Response = authenticatedGet(user1Token, AUTH_SERVICE_URL + "/api/v1/auth/me")
                .then().extract().response();
        user1Id = user1Response.jsonPath().getString("id");
        
        Response user2Response = authenticatedGet(user2Token, AUTH_SERVICE_URL + "/api/v1/auth/me")
                .then().extract().response();
        user2Id = user2Response.jsonPath().getString("id");
    }
    
    @Test
    @Order(1)
    void followUser_Success() {
        Response response = authenticatedPost(user1Token, 
                SOCIAL_SERVICE_URL + "/api/v1/social/follow/" + user2Id, null)
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
    }
    
    @Test
    @Order(2)
    void isFollowing_True() {
        Response response = authenticatedGet(user1Token, 
                SOCIAL_SERVICE_URL + "/api/v1/social/is-following/" + user2Id)
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertTrue(response.jsonPath().getBoolean("isFollowing"));
    }
    
    @Test
    @Order(3)
    void getFollowers_Success() {
        Response response = authenticatedGet(user2Token, 
                SOCIAL_SERVICE_URL + "/api/v1/social/followers/" + user2Id)
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertFalse(response.jsonPath().getList("content").isEmpty());
    }
    
    @Test
    @Order(4)
    void getFollowing_Success() {
        Response response = authenticatedGet(user1Token, 
                SOCIAL_SERVICE_URL + "/api/v1/social/following/" + user1Id)
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertFalse(response.jsonPath().getList("content").isEmpty());
    }
    
    @Test
    @Order(5)
    void getProfile_Success() {
        Response response = authenticatedGet(user2Token, 
                SOCIAL_SERVICE_URL + "/api/v1/social/profile/" + user2Id)
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("id"));
        assertNotNull(response.jsonPath().getString("username"));
    }
    
    @Test
    @Order(6)
    void unfollowUser_Success() {
        Response response = authenticatedDelete(user1Token, 
                SOCIAL_SERVICE_URL + "/api/v1/social/follow/" + user2Id)
                .then()
                .statusCode(204)
                .extract()
                .response();
        
        Response checkResponse = authenticatedGet(user1Token, 
                SOCIAL_SERVICE_URL + "/api/v1/social/is-following/" + user2Id)
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertFalse(checkResponse.jsonPath().getBoolean("isFollowing"));
    }
    
    @Test
    @Order(7)
    void searchUsers_Success() {
        Response response = authenticatedGet(user1Token, 
                SOCIAL_SERVICE_URL + "/api/v1/social/search?q=test&page=0&size=10")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getList("content"));
    }
}
