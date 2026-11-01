package com.vito.event.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InviteLinkConfig {

    // @Value (como S3Config en media-service): sin la variable en prod, el servicio no arranca
    @Bean
    public InviteLinkSettings inviteLinkSettings(@Value("${vito.invite-links.base-url}") String baseUrl) {
        return new InviteLinkSettings(baseUrl);
    }
}
