package com.vito.auth.infrastructure.adapter.inbound.rest;

import com.vito.auth.domain.exception.AuthenticationException;
import com.vito.auth.domain.port.inbound.UserDirectoryService;
import com.vito.auth.infrastructure.adapter.inbound.rest.dto.UserSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * Rutas {@code /api/v1/users/**} de la Ola 2a (spec 004, tag {@code Users}). Todas requieren
 * sesión: {@code SecurityConfig} ya lo exige y aquí se repite para responder el 401 del fixture.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserDirectoryService userDirectoryService;

    /**
     * {@code getUsersByIds}: {@code ?ids=a,b,c}. Sin {@code ids} es el mismo 400 que una lista
     * vacía (fixture getUsersByIds.400), no el error genérico de parámetro ausente.
     */
    @GetMapping
    public ResponseEntity<List<UserSummaryResponse>> getUsersByIds(
            @RequestParam(value = "ids", required = false) String ids) {
        requireSession();
        List<UserSummaryResponse> users = userDirectoryService.findByIds(parseIds(ids)).stream()
                .map(UserSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    /**
     * {@code searchUsers}. Quien busca sale del token validado aquí, no del header
     * {@code X-User-Id}: es la misma persona cuando la petición llega por el gateway, y no se
     * puede suplantar si alguien llama directo al servicio.
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserSummaryResponse>> searchUsers(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        String requesterId = requireSession();
        List<UserSummaryResponse> users = userDirectoryService.search(query, requesterId, page, size).stream()
                .map(UserSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    private static List<String> parseIds(String ids) {
        if (ids == null) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
    }

    /** @return el userId del token */
    private static String requireSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw AuthenticationException.invalidAccessToken();
        }
        return authentication.getName();
    }
}
