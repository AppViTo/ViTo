# ViTo · Checklist de seguridad

> **Estado: APROBADA (T051) el 2026-10-24 por AlexVilla (alexvilla01) y Bernardo (bamg359).**
> El Principio III de la constitución exige que este archivo no tenga `TODO` de severidad
> alta antes del primer release. Cada ítem de la versión anterior (sin fecha real,
> "2024-01-XX") tiene aquí una severidad, el estado real verificado en el código y la acción
> propuesta. Los ítems que ya estaban marcados `[x]` no se reauditaron, salvo los que se
> indican. La única excepción de severidad alta es **A3** (ver abajo): se aprueba para el
> evento de prueba del 2026-10-24 con las condiciones y la fecha de revisión indicadas.

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
| A1 | File type validation | **Resuelto (T-B19, #155):** media-service valida los magic bytes contra los MIME de CONVENTIONS §8 | Mientras rija la excepción A3, aceptar solo imágenes (T-B25) |
| A2 | File size limits | **Resuelto (T-B19, #155):** 10 MB en `media.max-file-size` y `spring.servlet.multipart` | — |
| A3 | Malware scanning | No existe; la constitución exige escaneo antimalware y moderación antes de publicar en el mosaico. ClamAV ocupa 1–1,5 GB de RAM y no cabe con holgura en el VPS de 12 GB. Hoy la subida está cerrada en `prod` (T-I17, #156) | **Excepción A3** (ver la sección siguiente) |
| A4 | Content-Security-Policy · X-Frame-Options (y HSTS, que pide la constitución) | **API resuelta (T-I18, #157):** HSTS, `nosniff`, `Referrer-Policy` y sin `Server` en `api.` y `live.`. **Web pendiente:** sigue en Vercel, que ignora `_headers` | CSP, HSTS y `X-Frame-Options` de la web con T-I11 (T031–T032 de la feature 001) |
| A5 | Container scanning | **Resuelto (T049):** Trivy en el job `build` de `cd.yml` | — |
| A6 | Vulnerability scanning (dependencias) | **Resuelto (T050 y T049):** Dependabot y Trivy | — |
| A7 | Kafka: SSL/TLS · Authorization · Topic access control | SASL/PLAIN sin TLS (`SASL_PLAINTEXT`), un solo usuario `vito`, sin ACL. La constitución pide SASL/TLS y ACL por tópico | **Excepción documentada:** broker y clientes en el mismo host, red interna de Compose sin puertos publicados (ADR-011 b). Revisión: cuando Kafka salga del host (Fase Crecimiento) o en 6 meses, lo que pase primero (T-I20) |
| A8 | *(fuera del checklist)* `GET /events/upcoming` lista eventos privados ajenos | **Resuelto (T-B17, #153)** | — |
| A9 | *(fuera del checklist)* Dependencias Java sin soporte | El primer escaneo real de Trivy (release 0.1.0, run 40158869241) encontró en los 10 servicios Java entre 39 y 57 CVE HIGH/CRITICAL con arreglo (5 a 10 CRITICAL por imagen): Spring Boot 3.2.12 sin soporte, con Spring Framework 6.1, Security 6.2, Tomcat 10.1.33, Netty, Jackson y kafka-clients viejos. Algunos solo tienen arreglo en Spring Framework 7 | **Excepción A9** (abajo) y subida de Spring Boot (T-B31, P0) |

### Excepción A3 · Subida al mosaico sin antimalware

- **Aprobada:** 2026-10-24 por los dos leads (T051). Responsable: Bernardo (backend).
- **Motivo:** no hay antimalware que quepa en el VPS de 12 GB, y el mosaico es requisito del
  evento de prueba.
- **Condiciones.** Todas deben estar desplegadas antes de abrir la subida en `prod` (T-I21):

  | Condición | Tarea |
  |-----------|-------|
  | Solo `image/jpeg`, `image/png` e `image/webp`; no se aceptan SVG, GIF, HEIC ni `video/mp4` en esta ola. CONVENTIONS §8 se ajusta en el contrato de la Ola 3 | T-B25, T-C07 (Ola 3) |
  | Magic bytes, MIME verificado en el servidor y ≤10 MB | T-B19 (#155) ✅ |
  | Re-codificación **síncrona en media-service** antes de guardar en R2: el original nunca se almacena y se eliminan los metadatos (EXIF, GPS) | T-B25 |
  | **Post-moderación del organizador:** la foto aparece al instante y el organizador la oculta o la retira; sin cola de aprobación | T-C07 (Ola 3), T-B26, T-F10 |

- **Revisión:** al cierre del evento de prueba o antes de la primera apertura a usuarios
  reales, lo que ocurra primero. Se cierra al integrar antimalware en Fase Crecimiento (S3
  Malware Protection o ClamAV dedicado).

### Excepción A9 · Trivy en modo informe para el release 0.1.0

- **Aprobada:** 2026-10-24 por Bernardo (`@bamg359`), con visto bueno previo y acuerdo entre
  los leads; Alex Villa la revisa al volver (mismo caso que el hotfix #192, ADR-012).
  Responsable: Bernardo (backend).
- **Motivo:** el evento de prueba no puede esperar la migración de Spring Boot. Varios CVE
  CRITICAL solo tienen arreglo en Spring Framework 7 (Spring Boot 4), que es una migración
  mayor, de días.
- **Qué cambia:** en `cd.yml`, Trivy sigue escaneando cada imagen y deja la tabla en el log,
  pero **no bloquea** el despliegue hasta el **2026-11-07 inclusive**. Desde el 2026-11-08
  vuelve a bloquear solo, por fecha, sin tocar el workflow.
- **Mitigación:** solo Caddy y SRS publican puertos. Los servicios Java quedan en la red
  interna detrás del gateway, con autenticación y rate limit, y el evento es cerrado. Lo que
  tenía arreglo fácil sí se corrigió: transcode y moderation sin `setuptools`/`wheel` y con
  las actualizaciones de Debian, y `mongo-backup` sin `/opt/js-yaml`, `gosu` ni las database
  tools que no usa.
- **Cierre:** T-B31 (Spring Boot con soporte en los 10 servicios) mergeada y desplegada con
  Trivy bloqueante y sin CVE HIGH/CRITICAL con arreglo fuera de `.trivyignore`. Si llega el
  2026-11-08 sin T-B31, los despliegues quedan bloqueados hasta resolverlo o hasta que los
  dos leads renueven la excepción.

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

## Aprobación de los leads (T051)

- **Fecha:** 2026-10-24.
- **Aprobado por:** AlexVilla (`alexvilla01`, lead frontend/contrato) y Bernardo (`bamg359`, lead backend).
- **Alcance:** triage y severidades de este archivo, y la excepción **A3** con sus
  condiciones y fecha de revisión. No exime de las tareas abiertas de severidad alta.
- **Trazabilidad de las altas:** A1/A2 → T-B19 (#155); A3 → excepción A3 (arriba; T-B25,
  T-B26, T-F10, T-I21 y la Ola 3 de T-C07); A4 → T-I18 (#157) en la API y T-I11 en la web;
  A5/A6 → T049/T050; A7 → excepción ya documentada (T-I20); A8 → T-B17 (#153). Las medias M1–M12 se cierran antes de abrir a usuarios reales; M1
  (blacklist de `jti`) quedó resuelta con T-B21 (#168).
- **Revisión de la excepción A3:** al cierre del evento de prueba, o antes de abrir a usuarios reales,
  lo que ocurra primero.
- **Definiciones de la excepción A3 (2026-10-24, Bernardo):** post-moderación del
  organizador, re-codificación síncrona en media-service y nombre "excepción A3" en lugar de
  "M1".

## Resumen

- **Altas del primer release:** A1, A2, A5, A6 y A8 resueltas; A4 resuelta en la API y
  pendiente en la web (T-I11); A3 cubierta por su excepción, con la subida cerrada en `prod`
  hasta cumplir sus condiciones (T-I21); A7 por su excepción.
- **Bloquean la apertura a usuarios o las tiendas:** M2–M12. M1 (revocación de access
  token) quedó resuelta con T-B21 (#168).
- **Tareas de las medias:** M2 → T-B22, M3 → T-B23, M6 → T-B24, M12 → T-I19.
