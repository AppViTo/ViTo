package com.vito.media;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long",
        "MONGODB_URI=mongodb://localhost:27017/test_db",
        "S3_ACCESS_KEY=test",
        "S3_SECRET_KEY=test"
})
class MediaServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
