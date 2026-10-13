package com.vito.gateway.security;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtAuthenticationFilterTest {

    @Test
    void rolesListFromAuthServiceBecomesCommaSeparatedHeader() {
        assertEquals("USER,ORGANIZER", JwtAuthenticationFilter.rolesHeader(List.of("USER", "ORGANIZER")));
    }

    @Test
    void singleRoleAndStringRolesArePassedThrough() {
        assertEquals("USER", JwtAuthenticationFilter.rolesHeader(List.of("USER")));
        assertEquals("USER,ADMIN", JwtAuthenticationFilter.rolesHeader("USER,ADMIN"));
    }

    @Test
    void missingRolesClaimBecomesEmptyHeader() {
        assertEquals("", JwtAuthenticationFilter.rolesHeader(null));
    }
}
