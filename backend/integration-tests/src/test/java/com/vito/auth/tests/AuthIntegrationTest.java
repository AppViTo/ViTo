package com.vito.auth.tests;

import com.vito.tests.utils.BaseIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class AuthIntegrationTest extends BaseIntegrationTest {
    
    private static String testUsername;
    private static String testEmail;
    private static final String testPassword = "TestPassword123!";
    
    @BeforeAll
    static void setup() {
        testUsername = "testuser_" + UUID.randomUUID().toString().substring(0, 8);
        testEmail = testUsername + "@test.com";
    }
    
    @Test
    @Order(1)
    void registerUser_Success() {
        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username", testUsername,
                        "email", testEmail,
                        "password", testPassword
                ))
                .post(AUTH_SERVICE_URL + "/api/v1/auth/register")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("token"));
        assertNotNull(response.jsonPath().getString("refreshToken"));
        assertEquals(testUsername, response.jsonPath().getString("user.username"));
    }
    
    @Test
    @Order(2)
    void registerUser_DuplicateUsername() {
        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username", testUsername,
                        "email", "another@test.com",
                        "password", testPassword
                ))
                .post(AUTH_SERVICE_URL + "/api/v1/auth/register")
                .then()
                .statusCode(400)
                .extract()
                .response();
        
        assertTrue(response.jsonPath().getString("message").contains("Username already exists"));
    }
    
    @Test
    @Order(3)
    void loginUser_Success() {
        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username", testUsername,
                        "password", testPassword
                ))
                .post(AUTH_SERVICE_URL + "/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("token"));
        testToken = response.jsonPath().getString("token");
        testUserId = response.jsonPath().getString("user.id");
    }
    
    @Test
    @Order(4)
    void loginUser_WrongPassword() {
        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username", testUsername,
                        "password", "WrongPassword123!"
                ))
                .post(AUTH_SERVICE_URL + "/api/v1/auth/login")
                .then()
                .statusCode(401)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("message"));
    }
    
    @Test
    @Order(5)
    void refreshToken_Success() {
        Response loginResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username", testUsername,
                        "password", testPassword
                ))
                .post(AUTH_SERVICE_URL + "/api/v1/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        String refreshToken = loginResponse.jsonPath().getString("refreshToken");
        
        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of("refreshToken", refreshToken))
                .post(AUTH_SERVICE_URL + "/api/v1/auth/refresh")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertNotNull(response.jsonPath().getString("token"));
        testToken = response.jsonPath().getString("token");
    }
    
    @Test
    @Order(6)
    void getProfile_Success() {
        Response response = authenticatedGet(testToken, AUTH_SERVICE_URL + "/api/v1/auth/me")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertEquals(testUsername, response.jsonPath().getString("username"));
        assertEquals(testEmail, response.jsonPath().getString("email"));
    }
    
    @Test
    @Order(7)
    void getProfile_Unauthorized() {
        RestAssured.given()
                .contentType(ContentType.JSON)
                .get(AUTH_SERVICE_URL + "/api/v1/auth/me")
                .then()
                .statusCode(401);
    }
    
    @Test
    @Order(8)
    void validateToken_Success() {
        Response response = RestAssured.given()
                .contentType(ContentType.JSON)
                .queryParam("token", testToken)
                .get(AUTH_SERVICE_URL + "/api/v1/auth/validate")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        assertTrue(response.jsonPath().getBoolean("valid"));
    }
    
    @Test
    @Order(9)
    void logout_Success() {
        Response response = authenticatedPost(testToken, AUTH_SERVICE_URL + "/api/v1/auth/logout", null)
                .then()
                .statusCode(204)
                .extract()
                .response();
    }
}
