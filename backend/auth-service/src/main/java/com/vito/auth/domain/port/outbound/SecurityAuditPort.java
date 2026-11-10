package com.vito.auth.domain.port.outbound;

/**
 * T-B24: registro de auditoría de los eventos de seguridad de la cuenta. El adaptador añade la
 * IP truncada y el {@code traceId}; nunca recibe ni registra el correo, la contraseña ni tokens
 * (Principio VI).
 */
public interface SecurityAuditPort {

    /**
     * @param userId id del usuario si se conoce; {@code null} (p. ej. login con un correo que no
     *               existe o refresh con un token ilegible).
     */
    void record(SecurityEvent event, String userId);

    enum SecurityEvent {
        REGISTER("register"),
        LOGIN_SUCCESS("login.success"),
        LOGIN_FAILURE("login.failure"),
        LOGOUT("logout"),
        REFRESH_REJECTED("refresh.rejected");

        private final String code;

        SecurityEvent(String code) {
            this.code = code;
        }

        /** Valor del campo {@code event} del log. */
        public String code() {
            return code;
        }
    }
}
