package com.vito.auth.infrastructure.adapter.outbound.audit;

import com.vito.auth.domain.port.outbound.SecurityAuditPort;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * T-B24: una línea JSON por evento en el logger {@code AUDIT}, con los campos {@code event},
 * {@code userId} (si existe) e {@code ip} truncada; {@code traceId} lo pone el encoder desde el
 * MDC. Consulta de ejemplo en Loki: {@code infrastructure/observability/README.md}.
 *
 * <p>La IP del cliente sale de {@code X-Forwarded-For}: cada proxy delante del servicio añade la
 * dirección de quien le habló, así que el cliente es la entrada número {@code trustedProxies}
 * contando desde la derecha (1 = solo el gateway; prod = Caddy + gateway = 2). Las entradas más a
 * la izquierda las controla el cliente y no se usan.
 */
@Component
public class Slf4jSecurityAuditAdapter implements SecurityAuditPort {

    static final String LOGGER_NAME = "AUDIT";

    private static final Logger AUDIT = LoggerFactory.getLogger(LOGGER_NAME);
    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}");

    private final int trustedProxies;

    public Slf4jSecurityAuditAdapter(@Value("${vito.audit.trusted-proxies:1}") int trustedProxies) {
        this.trustedProxies = trustedProxies;
    }

    @Override
    public void record(SecurityEvent event, String userId) {
        String ip = truncate(clientIp());
        if (userId != null) {
            AUDIT.info("audit {}", kv("event", event.code()), kv("userId", userId), kv("ip", ip));
        } else {
            AUDIT.info("audit {}", kv("event", event.code()), kv("ip", ip));
        }
    }

    private String clientIp() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return null;
        }
        HttpServletRequest request = servlet.getRequest();
        return clientIp(request.getHeader("X-Forwarded-For"), request.getRemoteAddr(), trustedProxies);
    }

    static String clientIp(String forwardedFor, String remoteAddr, int trustedProxies) {
        if (trustedProxies <= 0 || forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddr;
        }
        String[] hops = forwardedFor.split(",");
        int index = Math.max(0, hops.length - trustedProxies);
        return hops[index].strip();
    }

    /** IPv4 a /24 ({@code 203.0.113.0/24}), IPv6 a /48; cualquier otra cosa, {@code null}. */
    static String truncate(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        var v4 = IPV4.matcher(ip);
        if (v4.matches()) {
            return v4.group(1) + "." + v4.group(2) + "." + v4.group(3) + ".0/24";
        }
        if (ip.contains(":") && ip.matches("[0-9A-Fa-f:.\\[\\]]+")) {
            try {
                // Literal IPv6: no hay resolución DNS
                byte[] bytes = InetAddress.getByName(ip.replace("[", "").replace("]", "")).getAddress();
                if (bytes.length == 16) {
                    return String.format("%x:%x:%x::/48",
                            ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff),
                            ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff),
                            ((bytes[4] & 0xff) << 8) | (bytes[5] & 0xff));
                }
                if (bytes.length == 4) {
                    return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + "." + (bytes[2] & 0xff) + ".0/24";
                }
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }
}
