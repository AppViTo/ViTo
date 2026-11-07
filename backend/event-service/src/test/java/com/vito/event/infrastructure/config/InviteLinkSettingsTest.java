package com.vito.event.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** T004 (Ola 1, R4): base de los enlaces de invitación. */
class InviteLinkSettingsTest {

    @Test
    void linkUrlJoinsBaseAndTokenWithoutDoubleSlash() {
        assertEquals("https://app.example.test/i/abc",
                new InviteLinkSettings("https://app.example.test/").linkUrl("abc"));
        assertEquals("http://localhost:3000/i/abc",
                new InviteLinkSettings(" http://localhost:3000 ").linkUrl("abc"));
    }

    @Test
    void rejectsMissingOrInvalidBaseUrl() {
        for (String invalid : new String[]{null, "", "  ", "app.example.test", "ftp://app.example.test", "https://"}) {
            assertThrows(IllegalStateException.class, () -> new InviteLinkSettings(invalid), String.valueOf(invalid));
        }
    }

    @Test
    void contextFailsWithoutTheVariableInProd() {
        // application-prod.yml: base-url: ${INVITE_LINK_BASE_URL}, sin valor por defecto
        new ApplicationContextRunner()
                .withPropertyValues("vito.invite-links.base-url=${INVITE_LINK_BASE_URL}")
                .withUserConfiguration(InviteLinkConfig.class)
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void contextExposesSettingsWhenTheVariableIsSet() {
        new ApplicationContextRunner()
                .withPropertyValues("vito.invite-links.base-url=${INVITE_LINK_BASE_URL}",
                        "INVITE_LINK_BASE_URL=https://app.example.test")
                .withUserConfiguration(InviteLinkConfig.class)
                .run(context -> assertThat(context.getBean(InviteLinkSettings.class).baseUrl())
                        .isEqualTo("https://app.example.test"));
    }
}
