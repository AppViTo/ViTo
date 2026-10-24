package com.vito.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ADR-004 y T-B21: sin JWT_EXPIRATION_MS, el access token dura 15 min (expiresIn 900). */
class TokenLifetimeConfigTest {

    @Test
    void accessTokenDefaultsToFifteenMinutes() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        String expiration = new PropertyPlaceholderHelper("${", "}", ":", true)
                .replacePlaceholders(properties.getProperty("jwt.expiration"), new Properties());

        assertEquals("900000", expiration);
    }
}
