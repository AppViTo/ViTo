package com.vito.social.infrastructure.adapter.inbound.rest;

import com.vito.social.domain.port.inbound.SocialService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T033 (Ola 2a, FR-008): {@code GET /api/v1/social/search} se retiró (siempre devolvía vacío y
 * nunca estuvo en el contrato). La búsqueda es {@code searchUsers} de auth-service (ADR-016).
 */
class SocialSearchRetiredTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new SocialController(mock(SocialService.class)))
            .build();

    @Test
    void socialSearchRouteNoLongerExists() throws Exception {
        mvc.perform(get("/api/v1/social/search").param("query", "jose"))
                .andExpect(status().isNotFound());
    }

    @Test
    void socialServicePortHasNoSearch() {
        assertTrue(Arrays.stream(SocialService.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("search")));
    }
}
