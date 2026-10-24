# ViTo · Checklist de seguridad

> **Estado: PROPUESTA de triage (T051, 2026-10-22), pendiente de aprobación de los leads.**
> El Principio III de la constitución exige que este archivo no tenga `TODO` de severidad
> alta antes del primer release. Cada ítem de la versión anterior (sin fecha real,
> "2024-01-XX") tiene aquí una severidad, el estado real verificado en el código y la acción
> propuesta. Los ítems que ya estaban marcados `[x]` no se reauditaron, salvo los que se
> indican.

## Severidades

| Severidad | Criterio | Efecto |
|-----------|----------|--------|
| **Alta** | Explotable desde internet con el primer release, o exigido como MUST por la constitución | Bloquea el primer release salvo que se resuelva o quede como excepción documentada |
| **Media** | Hace falta antes de abrir a usuarios reales o de publicar en tiendas (D-10, D-12) | Bloquea la apertura, no el primer despliegue |
| **Baja** | Mejora o Fase Crecimiento | No bloquea |
| **Cerrado** | Ya está resuelto (con evidencia) o no aplica | Se marca `[x]` |

Una **excepción documentada** lleva: motivo, mitigación, quién la aprobó y fecha de
revisión. Las aprueban los dos leads en el PR que la agrega.

## Alta

| # | Ítem original | Estado real | Acción propuesta |
|---|---------------|-------------|------------------|
| A1 | File type validation | `MediaServiceImpl.validateFile` solo revisa el `Content-Type` que manda el cliente; no hay verificación de magic bytes | Validar magic bytes contra los MIME de CONVENTIONS §8 (tarea nueva, media-service) |
| A2 | File size limits | Límite de 100 MB (`media.max-file-size`, `spring.servlet.multipart`); la constitución pide 10 MB | Bajar a 10 MB en los dos lugares (misma tarea que A1) |
| A3 | Malware scanning | No existe; la constitución exige escaneo antimalware y moderación antes de publicar en el mosaico | **Opción 1:** el gateway no publica `/api/v1/media/**` ni las rutas de subida de `/api/v1/mosaic/**` en producción hasta la ola del mosaico (excepción documentada, cierra con esa ola). **Opción 2:** implementarlo antes del release. Se propone la 1 |
| A4 | Content-Security-Policy · X-Frame-Options (y HSTS, que pide la constitución) | Caddy no agrega cabeceras; T031 (feature 001) ya planea `X-Frame-Options` en la web, pero ni CSP ni HSTS | Ampliar T031: CSP compatible con Flutter web (`script-src 'self' 'wasm-unsafe-eval'`, CanvasKit servido desde el propio dominio) y `Strict-Transport-Security`; agregar HSTS y `X-Content-Type-Options` en `api.` y `live.` en el Caddyfile |
| A5 | Container scanning | No existe | Cubierto por T049 (Trivy en `cd.yml`, feature 001) |
| A6 | Vulnerability scanning (dependencias) | No existe | Cubierto por T050 (Dependabot) y T049 |
| A7 | Kafka: SSL/TLS · Authorization · Topic access control | SASL/PLAIN sin TLS (`SASL_PLAINTEXT`), un solo usuario `vito`, sin ACL. La constitución pide SASL/TLS y ACL por tópico | **Excepción documentada:** broker y clientes en el mismo host, red interna de Compose sin puertos publicados (ADR-011 b). Revisión: cuando Kafka salga del host (Fase Crecimiento) o en 6 meses, lo que pase primero |
| A8 | *(fuera del checklist)* `GET /events/upcoming` lista eventos privados ajenos | Bug abierto, T-B17 (P0, 🟢 READY) | Resolver T-B17 antes del release: expone ubicaciones de eventos privados |

## Media

| # | Ítem original | Estado real | Acción propuesta |
|---|---------------|-------------|------------------|
| M1 | Token blacklisting for immediate revocation | **Resuelto (T-B21).** El logout guarda el `jti` del access token en Redis (`revoked_jti:<jti>`, TTL = vida restante) y el gateway responde `401` / cierre `4401` a los tokens revocados. Si Redis no responde en 250 ms, el gateway falla abierto (sigue validando firma y vencimiento) y cuenta el fallo en `vito.gateway.revocation.check.errors` | Alertar sobre `vito.gateway.revocation.check.errors` junto con M7 |
| M2 | Account lockout after failed attempts | Hay rate limit por IP en las rutas públicas (T-B01, T-B11); no hay límite por cuenta | Throttling por cuenta con espera creciente (no bloqueo duro, que permitiría bloquear cuentas ajenas) |
| M3 | User-based rate limiting | El rate limit actual es por IP | Bucket por `X-User-Id` en las rutas autenticadas del gateway |
| M4 | Database encryption at rest | MongoDB Community no cifra en reposo; el disco del VPS tampoco | Excepción documentada en Fase Tracción (acceso al VPS solo por llave, datastores sin puertos); revisar con D-12 |
| M5 | Sensitive data encryption | No está definido qué dato es sensible | Definirlo con D-12 (teléfono, ubicación de eventos privados) y decidir cifrado por campo |
| M6 | Audit logging implemented | No existe | Registrar login, cambio de contraseña, borrado de cuenta y cambios de rol, sin datos personales en claro (Principio IX) |
| M7 | Security event monitoring | No existe; T-I13 trae alertas de caída y recursos | Agregar alertas de picos de 401 y 429 en el gateway a T040 (feature 001) |
| M8 | Error logging implemented | Lo cubren los handlers de errores; faltan en 6 servicios | Lo cierra T-B03 (ADR-017) |
| M9 | Recovery procedures documented | `infrastructure/backup/restore.sh` existe, sin runbook | Lo cierra T-I14 |
| M10 | GDPR compliance · CCPA compliance | No aplican tal cual: el marco es la Ley 1581 de 2012 (Colombia) | Reemplazar por "Ley 1581" y resolverlo con D-12 (política de privacidad antes de la prueba cerrada en Google Play) |
| M11 | Data retention policies · User data export · User data deletion | No existen; Google Play exige un mecanismo de borrado de cuenta | D-12 y el flujo `USER_DELETED` del Principio IX, en la Ola 1 |
| M12 | *(fuera del checklist)* Credenciales de Mongo por servicio | Los 10 servicios Java usan el usuario **root** (`MONGODB_URI` en `x-java-service` de `docker-compose.prod.yml`). Si se compromete un servicio, quedan expuestas todas las bases, también la de auth, y nada hace cumplir el Principio II ("dueño exclusivo de su base") | Un usuario por servicio con rol `readWrite` solo en su base, creado por un script idempotente como `ensure-ro-user.sh` (feature 001); `MONGODB_URI` por servicio |

## Baja

| # | Ítem original | Estado real | Acción propuesta |
|---|---------------|-------------|------------------|
| B1 | Password complexity requirements | Mínimo de 8 caracteres | NIST 800-63B desaconseja reglas de composición: a futuro, revisar la contraseña contra listas de contraseñas filtradas |
| B2 | Concurrent session limits | No existe | Fase Crecimiento |
| B3 | HTML sanitization for user content | Flutter muestra texto, no HTML | Revisar solo si la galería pública del archivo llega a mostrar HTML |
| B4 | Credentials handling secured | El gateway tiene `allow-credentials: true`, pero la app usa Bearer, no cookies | Pasar a `false` cuando se toque la configuración de CORS |
| B5 | Redis: persistence security | AOF en un volumen sin puertos publicados | Sin acción en Fase Tracción |
| B6 | Intrusion detection | `fail2ban` en SSH (T023, feature 001) | Fase Crecimiento |
| B7 | License compliance checked | No existe | Revisarla antes de publicar en tiendas |
| B8 | Admin endpoints | No existe ninguno | Cuando existan, van con rol y en el contrato |

## Cerrado (con evidencia)

| Ítem original | Evidencia |
|---------------|-----------|
| Session timeout configuration | Lo cubre el vencimiento del JWT (access y refresh) |
| SQL injection prevention | No aplica (MongoDB) |
| NoSQL injection prevention | Sin `@Query` ni `BasicQuery` con texto del usuario en `backend/`; Spring Data parametriza las consultas derivadas. Volver a revisar si aparece alguna |
| Rate limiting implementation · IP-based rate limiting | T-B01 (#19) y T-B11 (#24): rate limit por IP en las rutas públicas del gateway, activo en todos los perfiles |
| CORS origins restricted | Gateway: `CORS_ALLOWED_ORIGINS` obligatorio en `docker-compose.prod.yml`. *Pendiente funcional, no de seguridad:* chat-service tiene los orígenes WS fijos en `localhost` y bloqueará a la web en producción |
| Resource limits configured | `mem_limit` en todos los servicios de `docker-compose.prod.yml` (los Java lo heredan de `x-java-service`) |
| MongoDB · Redis: network isolation | Sin `ports:` en `docker-compose.prod.yml`; solo Caddy (80/443) y SRS (1935, 8000/udp) publican puertos |
| Automated backups configured | `mongo-backup` diario a R2 con retención de 14 días (T-I07, T-I09) |
| Backup encryption (MongoDB y §11) | R2 cifra los objetos en reposo (AES-256) y la subida va por TLS; el token de R2 está limitado al bucket. Cifrado del lado del cliente (`age`): opcional, Baja |

## Ítems marcados `[x]` que conviene revisar

- **"Dependencies regularly updated"**: no hay proceso. Queda cubierto por T050 (Dependabot).
- **"Sensitive data excluded from logs"**: falta verificarlo. Lo comprueban T034/T035 (enmascarado en Alloy) y T053 (logs JSON) de la feature 001.

## Resumen

- **Bloquean el primer release:** A1, A2, A4, A8 (código), A5 y A6 (ya planificadas en la
  feature 001), A3 (excepción propuesta: no publicar las rutas de subida) y A7 (excepción
  propuesta).
- **Bloquean la apertura a usuarios o las tiendas:** M1–M12.
- **Tareas nuevas que habría que crear en `docs/roadmap/tasks.md`:** validación de subida
  (A1 + A2), CSP y HSTS (A4, ampliando T031 y el Caddyfile), rutas de subida cerradas en
  producción (A3), throttling por cuenta (M2), rate limit por usuario (M3), audit logging
  (M6) y usuarios de Mongo por servicio (M12).
