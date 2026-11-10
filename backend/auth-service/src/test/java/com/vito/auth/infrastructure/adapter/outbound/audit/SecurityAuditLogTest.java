package com.vito.auth.infrastructure.adapter.outbound.audit;

import com.vito.auth.domain.exception.AuthenticationException;
import com.vito.auth.domain.model.User;
import com.vito.auth.domain.port.inbound.AuthService;
import com.vito.auth.domain.port.outbound.CachePort;
import com.vito.auth.domain.port.outbound.EventPublisherPort;
import com.vito.auth.domain.port.outbound.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * T-B24 de punta a punta con el encoder JSON real (logback-spring.xml): cada evento de seguridad
 * deja una línea en el logger {@code AUDIT} con {@code event}, {@code userId}, {@code ip} truncada
 * y {@code traceId}, y ninguna línea del servicio (de auditoría o no) contiene el correo, la
 * contraseña ni los tokens (Principio VI).
 */
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "JWT_SECRET=test-secret-key-for-unit-tests-only-256-bits-long",
        "MONGODB_URI=mongodb://localhost:27017/test_db"
})
class SecurityAuditLogTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String EMAIL = "audit.person@vito.test";
    private static final String PASSWORD = "Sup3r-secret-pass";
    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

    /** Campos que puede tener una línea de auditoría; cualquier otro es una fuga potencial. */
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "@timestamp", "@version", "message", "logger_name", "thread_name", "level", "level_value",
            "service", "traceId", "spanId", "event", "userId", "ip");

    @MockitoBean
    UserRepository userRepository;

    @MockitoBean
    CachePort cachePort;

    @MockitoBean
    EventPublisherPort eventPublisher;

    @Autowired
    AuthService authService;

    @Autowired
    PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.18.0.9");
        request.addHeader("X-Forwarded-For", "203.0.113.77");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        MDC.put("traceId", TRACE_ID);

        user = User.builder().id("usr_audit").username("audit_person").email(EMAIL)
                .passwordHash(passwordEncoder.encode(PASSWORD)).roles(List.of("USER"))
                .profile(User.Profile.builder().displayName("Audit").build())
                .build();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        MDC.clear();
    }

    @Test
    void everySecurityEventWritesOneAuditLineWithoutPersonalData(CapturedOutput output) {
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.save(any())).thenReturn(user);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.findByEmail("unknown@vito.test")).thenReturn(Optional.empty());
        when(userRepository.findById("usr_audit")).thenReturn(Optional.of(user));

        AuthService.AuthResult registered = authService.register(AuthService.RegisterCommand.builder()
                .username("audit_person").email(EMAIL).password(PASSWORD).displayName("Audit").build());
        AuthService.AuthResult loggedIn = authService.login(
                AuthService.LoginCommand.builder().email(EMAIL).password(PASSWORD).build());
        assertThrows(AuthenticationException.class, () -> authService.login(
                AuthService.LoginCommand.builder().email(EMAIL).password("wrong-" + PASSWORD).build()));
        assertThrows(AuthenticationException.class, () -> authService.login(
                AuthService.LoginCommand.builder().email("unknown@vito.test").password(PASSWORD).build()));
        when(cachePort.isBlacklisted(loggedIn.getRefreshToken())).thenReturn(true);
        assertThrows(AuthenticationException.class, () -> authService.refreshToken(
                AuthService.RefreshTokenCommand.builder().refreshToken(loggedIn.getRefreshToken()).build()));
        authService.logout(loggedIn.getAccessToken());

        List<JsonNode> audit = auditLines(output.getAll());
        List<String> events = audit.stream().map(line -> line.get("event").asText()).toList();
        assertEquals(List.of("register", "login.success", "login.failure", "login.failure",
                "refresh.rejected", "logout"), events);

        for (JsonNode line : audit) {
            assertEquals("203.0.113.0/24", line.get("ip").asText(), line.toString());
            assertEquals(TRACE_ID, line.get("traceId").asText(), line.toString());
            assertEquals("auth-service", line.get("service").asText(), line.toString());
            Set<String> fields = new HashSet<>();
            line.propertyNames().forEach(fields::add);
            assertTrue(ALLOWED_FIELDS.containsAll(fields), "campos inesperados: " + fields);
        }
        // Solo el login con un correo inexistente va sin userId
        assertEquals(List.of("usr_audit", "usr_audit", "usr_audit", "", "usr_audit", "usr_audit"),
                audit.stream().map(line -> line.has("userId") ? line.get("userId").asText() : "").toList());

        String logs = output.getAll();
        for (String secret : List.of(EMAIL, "unknown@vito.test", PASSWORD, registered.getAccessToken(),
                registered.getRefreshToken(), loggedIn.getAccessToken(), loggedIn.getRefreshToken())) {
            assertFalse(logs.contains(secret), "el log contiene un dato prohibido");
        }
    }

    private static List<JsonNode> auditLines(String output) {
        List<JsonNode> lines = new ArrayList<>();
        for (String raw : output.split("\\R")) {
            if (!raw.startsWith("{")) {
                continue;
            }
            JsonNode node = MAPPER.readTree(raw);
            if (Slf4jSecurityAuditAdapter.LOGGER_NAME.equals(node.path("logger_name").asText())) {
                lines.add(node);
            }
        }
        return lines;
    }

    @Test
    void clientIpIsTheEntryAddedByTheLastTrustedProxy() {
        // Solo el gateway delante (dev, docker): la última entrada
        assertEquals("198.51.100.4", Slf4jSecurityAuditAdapter.clientIp("1.2.3.4, 198.51.100.4", "172.18.0.2", 1));
        // Caddy + gateway (prod): la penúltima; la primera la puso el cliente
        assertEquals("198.51.100.4",
                Slf4jSecurityAuditAdapter.clientIp("1.2.3.4, 198.51.100.4, 172.18.0.5", "172.18.0.2", 2));
        // Menos entradas que proxies: la más cercana al cliente
        assertEquals("198.51.100.4", Slf4jSecurityAuditAdapter.clientIp("198.51.100.4", "172.18.0.2", 2));
        // Sin cabecera: la conexión
        assertEquals("172.18.0.2", Slf4jSecurityAuditAdapter.clientIp(null, "172.18.0.2", 1));
    }

    @Test
    void ipIsTruncated() {
        Map.of(
                "203.0.113.77", "203.0.113.0/24",
                "2001:db8:85a3:8d3:1319:8a2e:370:7348", "2001:db8:85a3::/48",
                "[2001:db8::1]", "2001:db8:0::/48",
                "not-an-ip", "",
                "", ""
        ).forEach((ip, expected) -> assertEquals(expected,
                String.valueOf(Optional.ofNullable(Slf4jSecurityAuditAdapter.truncate(ip)).orElse("")), ip));
    }
}
