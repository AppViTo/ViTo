package com.vito.shared.web.security;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** T025 (R6): el filtro expone el userId y el nombre visible a los controladores. */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-key-for-unit-tests-only-256-bits-long";

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private AuthenticatedUser authenticate(JwtBuilder token, String headerName) throws Exception {
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer "
                + token.signWith(Keys.hmacShaKeyFor(SECRET.getBytes())).compact());
        if (headerName != null) {
            request.addHeader("X-User-Name", headerName);
        }
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("usr_1", authentication.getName());
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    @Test
    void theSignedNameClaimWins() throws Exception {
        AuthenticatedUser user = authenticate(Jwts.builder().subject("usr_1").claim("name", "Alex Villa"), "Otro");

        assertEquals("Alex Villa", user.displayName());
    }

    @Test
    void withoutTheClaimTheGatewayHeaderIsUsed() throws Exception {
        assertEquals("Ana", authenticate(Jwts.builder().subject("usr_1"), "  Ana ").displayName());
    }

    @Test
    void withoutClaimOrHeaderTheNameIsNull() throws Exception {
        assertNull(authenticate(Jwts.builder().subject("usr_1"), null).displayName());
        assertNull(authenticate(Jwts.builder().subject("usr_1").claim("name", " "), null).displayName());
    }

    @Test
    void aVeryLongNameIsCut() throws Exception {
        String name = authenticate(Jwts.builder().subject("usr_1").claim("name", "x".repeat(300)), null).displayName();

        assertEquals(AuthenticatedUser.MAX_DISPLAY_NAME, name.length());
    }

    /** Sin Authorization, con otro esquema, con firma ajena o vencido: sigue sin autenticación. */
    @Test
    void invalidOrMissingTokensLeaveTheRequestAnonymous() throws Exception {
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        String otherKey = Jwts.builder().subject("usr_1")
                .signWith(Keys.hmacShaKeyFor("otra-clave-de-pruebas-de-256-bits-como-minimo!!".getBytes())).compact();
        String expired = Jwts.builder().subject("usr_1").expiration(new Date(0))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes())).compact();

        for (String header : new String[]{null, "Basic dXNyOnB3", "Bearer no-es-un-jwt",
                "Bearer " + otherKey, "Bearer " + expired}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            if (header != null) {
                request.addHeader("Authorization", header);
            }
            MockFilterChain chain = new MockFilterChain();
            filter.doFilter(request, new MockHttpServletResponse(), chain);

            assertNull(SecurityContextHolder.getContext().getAuthentication(), header);
            assertEquals(request, chain.getRequest(), "la cadena sigue: " + header);
        }
    }
}
