package com.vito.media.infrastructure.config;

/**
 * Conexión al almacenamiento de objetos con API S3 (ADR-011): SeaweedFS en local,
 * Cloudflare R2 en producción. Todo sale de variables de entorno (ver {@code .env.example}).
 *
 * @param endpoint     URL de la API S3 (R2: {@code https://<account-id>.r2.cloudflarestorage.com})
 * @param region       región de firma; R2 usa {@code auto}
 * @param pathStyle    {@code true} → {@code <endpoint>/<bucket>/<key>}; lo exige SeaweedFS
 * @param createBucket crear el bucket al arrancar si no existe; en R2 el token no tiene ese permiso
 * @param publicUrl    base de las URLs públicas; vacío → {@code <endpoint>/<bucket>}
 */
public record S3Settings(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket,
        boolean pathStyle,
        boolean createBucket,
        String publicUrl) {

    public String publicBaseUrl() {
        String base = (publicUrl == null || publicUrl.isBlank())
                ? endpoint + "/" + bucket
                : publicUrl;
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
