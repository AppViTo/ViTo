package com.vito.tests.utils;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;

import java.util.Map;

public class BaseIntegrationTest {
    
    protected static final String BASE_URL = "http://localhost";
    protected static final String AUTH_SERVICE_URL = BASE_URL + ":8081";
    protected static final String SOCIAL_SERVICE_URL = BASE_URL + ":8082";
    protected static final String EVENT_SERVICE_URL = BASE_URL + ":8083";
    protected static final String CHECKIN_SERVICE_URL = BASE_URL + ":8084";
    protected static final String MOSAIC_SERVICE_URL = BASE_URL + ":8085";
    protected static final String STREAMING_SERVICE_URL = BASE_URL + ":8086";
    protected static final String ARCHIVE_SERVICE_URL = BASE_URL + ":8087";
    
    protected static String testToken;
    protected static String testUserId;
    
    @BeforeAll
    static void baseSetup() {
        RestAssured.baseURI = BASE_URL;
        RestAssured.useRelaxedHTTPSValidation();
    }
    
    protected String registerAndGetToken(String username, String email, String password) {
        Response registerResponse = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username", username,
                        "email", email,
                        "password", password
                ))
                .post(AUTH_SERVICE_URL + "/api/v1/auth/register")
                .then()
                .statusCode(200)
                .extract()
                .response();
        
        return registerResponse.jsonPath().getString("token");
    }
    
    protected Response authenticatedRequest(String token) {
        return RestAssured.given()
                .contentType(ContentType.JSON)
                .header("Authorization", "Bearer " + token);
    }
    
    protected Response authenticatedGet(String token, String url) {
        return authenticatedRequest(token)
                .get(url);
    }
    
    protected Response authenticatedPost(String token, String url, Object body) {
        return authenticatedRequest(token)
                .body(body)
                .post(url);
    }
    
    protected Response authenticatedPut(String token, String url, Object body) {
        return authenticatedRequest(token)
                .body(body)
                .put(url);
    }
    
    protected Response authenticatedDelete(String token, String url) {
        return authenticatedRequest(token)
                .delete(url);
    }
}
