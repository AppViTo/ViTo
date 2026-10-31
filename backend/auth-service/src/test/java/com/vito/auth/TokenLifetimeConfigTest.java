package com.vito.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ADR-004 y T-B21: sin JWT_EXPIRATION_MS, el access token dura 15 min (expiresIn 900). */
class TokenLifetimeConfigTest {

    @Test
    void accessTokenDefaultsToFifteenMinutes() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        Properties properties = yaml.getObject();

        // Sin JWT_EXPIRATION_MS en el entorno, el placeholder cae en su valor por defecto
        Matcher placeholder = Pattern.compile("^\\$\\{[A-Z_]+:(\\d+)}$")
                .matcher(properties.getProperty("jwt.expiration"));
        assertTrue(placeholder.matches(), "jwt.expiration sin valor por defecto");

        assertEquals("900000", placeholder.group(1));
    }
}
