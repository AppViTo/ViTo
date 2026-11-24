package com.vito.social.domain.exception;

/**
 * auth-service no pudo dar los datos de identidad a tiempo (FR-012a): el perfil o la lista no se
 * arman con datos inventados, se responde {@code 503 SERVICE_UNAVAILABLE} y el usuario reintenta.
 */
public class UserDirectoryUnavailableException extends RuntimeException {

    /** Mismo texto que los fixtures {@code social/*.503.json} (CONVENTIONS §3.3). */
    public static final String MESSAGE = "No pudimos cargar los datos de los usuarios. Intenta de nuevo en unos segundos";

    public UserDirectoryUnavailableException() {
        super(MESSAGE);
    }

    public UserDirectoryUnavailableException(Throwable cause) {
        super(MESSAGE, cause);
    }
}
