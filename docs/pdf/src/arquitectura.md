---
title: Arquitectura de ViTo
subtitle: Fase Tracción
version: 1.2
date: 2026-10-30
audience: Técnica
status: Borrador
---

# Resumen y alcance

Este documento describe la arquitectura **vigente** de ViTo (VibeTogether) en la **Fase Tracción**: lo que corre hoy en producción y en el entorno local, no el diseño original. La referencia de despliegue es el ADR-011; todo lo que el diseño original (Kubernetes, ArgoCD, Eureka, Dragonfly, MinIO/S3 en producción) decía distinto queda reemplazado.

| Pregunta | Respuesta corta |
|----------|-----------------|
| ¿Qué es ViTo? | Plataforma de ciclo de vida de eventos sociales. |
| ¿Dónde corre? | Un VPS (Contabo, 12 GB) con Docker Compose, detrás de Caddy. |
| ¿Cuántos servicios? | 10 Spring Boot (gateway incluido) y 3 FastAPI en el repo; 2 de los FastAPI se despliegan. |
| ¿Cómo se comunican? | REST vía gateway, WebSocket crudo vía gateway, Kafka entre servicios, Redis. |
| ¿Quién manda ante una contradicción? | Constitución > ADR > CONVENTIONS > contrato > CONTRIBUTING > roadmap > AGENTS > código. |

**Aviso sobre fuentes obsoletas.** `docs/architecture.md`, la tabla de stack y la estructura de carpetas de `AGENTS.md` describen el diseño original. Este documento solo los menciona cuando se rotulan como "diseño original".

Fuente: docs/decisions/adr-011-traction-stack.md, docs/handoff/AGENT-BRIEF.md (secciones 2 y 6), .specify/memory/constitution.md (Perfiles de Despliegue).

# Visión del producto y ciclo de vida del evento

ViTo integra en una sola plataforma lo que normalmente se resuelve con varias herramientas: creación de eventos e invitaciones digitales, RSVP con opciones múltiples, QR de acceso, mosaico en vivo durante el evento, streaming unidireccional con reacciones y archivo posterior con opción de compartir.

## Ciclo de vida

| Etapa | Qué ocurre | Servicio dueño (ADR-016) |
|-------|------------|---------------------------|
| 1. Crear | El organizador crea el evento (`title`, `details`, `settings`, `stats`, `status`; ADR-001) | event-service |
| 2. Invitar | Invitaciones en app y por enlace | event-service |
| 3. RSVP | Aceptar, rechazar, quizá, lista de espera | event-service |
| 4. QR | Se emite un ticket QR por invitado | checkin-service |
| 5. Check-in | Se valida el QR en la puerta (meta < 200 ms p99) | checkin-service |
| 6. Mosaico en vivo | Grid y feed de fotos y videos de los asistentes | media-service (subida) y mosaic-service (ítem del mosaico) |
| 7. Streaming | El anfitrión transmite; los espectadores reaccionan | streaming-service con SRS |
| 8. Chat | Salas de mensajes (también la del stream) | chat-service |
| 9. Archivo | El evento se congela, se genera la galería y se comparte | archive-service |

Social (seguir, perfiles) y Auth (registro, login, tokens) son transversales: social-service y auth-service.

## Perfil de carga de la Fase Tracción

Eventos de hasta unos 150 asistentes y unos 100 espectadores por stream. Los SLO de latencia se mantienen; cambia la carga con la que se miden (constitución, Principio VII).

| Métrica | Objetivo |
|---------|----------|
| Validación de QR | < 200 ms |
| Latencia del mosaico en vivo | < 3 s |
| Latencia de reacciones del stream | < 1 s |
| Lectura de listados | < 300 ms |

Fuente: AGENTS.md (visión y KPI), docs/decisions/adr-001-event-structure.md, adr-011 (perfil de carga), adr-016.

# Mapa de servicios

La verificación contra el repo (carpetas `backend/`, `ai-services/` y los tres archivos `docker-compose*.yml`) da el siguiente inventario.

## Servicios Java (Spring Boot, arquitectura hexagonal)

Los 10 se despliegan en producción (`docker-compose.prod.yml`). Los puertos son los de `application.yml`.

| Servicio | Puerto | Responsabilidad | Dueño de dominio (ADR-016) |
|----------|-------:|-----------------|-----------------------------|
| api-gateway | 8080 (gestión 9080) | Única puerta de entrada: valida JWT, inyecta `X-User-*`, CORS, rate limit, circuit breaker, proxy REST y `/ws/**` | Borde y enrutamiento |
| auth-service | 8081 | Registro, login, refresh, logout, `GET /auth/me` | Identidad y tokens |
| social-service | 8082 | Grafo social: seguir, dejar de seguir, perfiles | Relaciones sociales |
| event-service | 8083 | CRUD de eventos, invitaciones, RSVP, listado con `scope` (ADR-014) | Eventos, invitaciones y RSVP |
| checkin-service | 8084 | Ticket QR, validación, estadísticas, recientes, revocación | QR y check-in (cupo del check-in) |
| mosaic-service | 8085 | Ítems del mosaico (grid y feed), moderación del organizador | Contenido del mosaico |
| streaming-service | 8086 | Sesiones de stream, integración con SRS, reacciones | Streams |
| archive-service | 8087 | Galería post-evento y enlace para compartir | Archivo del evento |
| chat-service | 8088 | Salas y mensajes por WebSocket crudo | Chat (incluido el del stream) |
| media-service | 8089 | Subida y procesamiento de archivos, almacenamiento S3 (R2 en prod) | Subida de medios |

## Servicios Python (FastAPI)

| Servicio | Puerto | En el repo | En compose local | En producción |
|----------|-------:|:----------:|:----------------:|:-------------:|
| transcode-service | 8000 | Sí | Sí | Sí (768 MiB, ffmpeg) |
| recommendation-service | 8001 | Sí | Sí | **No** (diferido, ADR-011 d) |
| moderation-service | 8002 | Sí | Sí | Sí (512 MiB) |

El gateway enruta `/api/v1/recommendations/**` aunque en producción el servicio no exista; la degradación está pendiente en T-I23.

## Infraestructura de apoyo

| Componente | Local (`docker-compose.yml`) | Producción (`docker-compose.prod.yml`) |
|------------|------------------------------|-----------------------------------------|
| MongoDB | mongo 7.0, puerto 27017 publicado | mongo 7.0.14, sin puerto público, 1 GiB |
| Redis | redis 7.2, puerto 6379 publicado | redis 7.2.6, maxmemory 192 MB |
| Kafka | cp-kafka 7.5.0 con ZooKeeper | apache/kafka 3.8.1 modo KRaft, 1 broker |
| SRS | ossrs/srs 5 | ossrs/srs 5.0.213 |
| Almacenamiento de objetos | SeaweedFS (S3 en `localhost:9000`) | Cloudflare R2 (API S3) |
| Backup de Mongo | perfil `backup` | contenedor `mongo-backup`, diario a R2 |
| Borde TLS | no hay | Caddy 2.8.4 |

## Fuera de alcance de Fase Tracción

- `infrastructure/kubernetes/` existe en el repo pero **no se trabaja**; es del diseño original y de la Fase Crecimiento.
- No hay Eureka: el descubrimiento es el DNS de Docker Compose.

Fuente: backend/*/src/main/resources/application.yml, docker-compose.yml, docker-compose.prod.yml, docs/decisions/adr-016-domain-owners.md, adr-011.

# Comunicación entre componentes

## Diagrama de componentes (despliegue vigente)

```
  Flutter (Android / web)
  Web: app.vito.app (Cloudflare Pages)
        |  HTTPS / WSS
        v
  +------------------------ VPS (Docker Compose, red "vito") -------------------------+
  |                                                                                   |
  |  Caddy :80/:443  (TLS Let's Encrypt)                                              |
  |    api.vito.app  --> api-gateway :8080  (REST /api/v1/** y WS /ws/**)                |
  |    live.vito.app --> srs :1985 (WHIP/WHEP) y :8080 (HLS/FLV)                         |
  |                                                                                   |
  |  api-gateway (JWT, X-User-*, CORS, rate limit, circuit breaker, revocacion jti)   |
  |    |-- auth :8081      |-- social :8082     |-- event :8083    |-- checkin :8084   |
  |    |-- mosaic :8085    |-- streaming :8086  |-- archive :8087  |-- chat :8088      |
  |    |-- media :8089     |-- transcode :8000  '-- moderation :8002                  |
  |                                                                                   |
  |  servicios Java --> kafka (1 broker, KRaft)       servicios --> redis             |
  |  servicios      --> mongodb (autenticado, sin puerto publico)                     |
  |  media / transcode --> Cloudflare R2 (S3)         streaming --> srs               |
  |  mongo-backup --> mongodump diario --> Cloudflare R2                              |
  +-----------------------------------------------------------------------------------+
        ^ RTMP :1935 y WebRTC :8000/udp (SRS, publicacion del anfitrion)

  Observabilidad: servicios --> Grafana Cloud (plan gratuito)   [Pendiente: T-I13]
  CI/CD: GitHub Actions --> docker save | ssh docker load --> VPS (sin registro)
```

El dominio real es `vito.app`: la web vive en `https://app.vito.app`, la API en `api.vito.app` y el stream en `live.vito.app`. El ejemplo `api.vito.com` de CONVENTIONS es solo ilustrativo.

Solo Caddy (80/443) y SRS (RTMP 1935 y WebRTC 8000/udp) publican puertos en producción. Mongo, Redis y Kafka no son alcanzables desde fuera.

## REST vía gateway

- Base: `/api/v1/<recurso>`. **Toda** llamada pasa por el gateway; nunca se llama a un servicio directo.
- El gateway tiene una ruta por servicio (`/api/v1/auth/**`, `/social/**`, `/events/**`, `/checkin/**`, `/mosaic/**`, `/streaming/**`, `/archive/**`, `/chat/**`, `/media/**`, `/transcode/**`, `/recommendations/**`, `/moderation/**`), cada una con circuit breaker y rate limiter.
- **Headers de contexto (ADR-007):** el gateway valida el JWT y añade `X-User-Id`, `X-User-Email` y `X-User-Roles` (CSV). Elimina esos headers de cualquier request entrante antes de reenviar (anti-spoofing). Los servicios nunca los aceptan de un cliente.
- Regla general (ADR-015 y ADR-014): ninguna ruta acepta la identidad del usuario como parámetro o campo enviado por el cliente.

## WebSocket crudo (ADR-006, ADR-015)

| Recurso | Ruta (vía gateway) | Servicio |
|---------|--------------------|----------|
| Chat | `/ws/chat?token=<jwt>` | chat-service |
| Check-in | `/ws/checkin/{eventId}?token=<jwt>` | checkin-service |
| Mosaico | `/ws/mosaic/{eventId}?token=<jwt>` | mosaic-service |
| Streaming | `/ws/streaming/{streamId}?token=<jwt>` | streaming-service |

- Protocolo: WebSocket **crudo** con frames JSON `{ "type": "SCREAMING_SNAKE", "data": { } }`. Sin STOMP ni SockJS.
- El gateway valida `?token=` en el handshake; token inválido cierra con código `4401`.
- chat-service toma la identidad del header `X-User-Id` del handshake y autoriza cada frame por sala.
- `docs/api/asyncapi.yaml` existe en el repo, pero la tarea T-C05 (v0 con las 4 rutas) sigue en estado RFC: su contenido no es contrato aprobado hasta que un CODEOWNER lo apruebe.

## Kafka

Tópicos que el código publica hoy: `user-events`, `event-events`, `checkin-events`, `social-events`, `media-events`, `mosaic-events`, `streaming-events`, `archive-events` y `comment-events`.

- **Estado real:** no hay ningún `@KafkaListener` en `backend/*/src`. Hoy solo hay productores; nadie consume (ADR-016 lo confirma para `checkin-events`).
- El primer consumidor previsto es mosaic-service sobre `checkin-events` (Ola 3).
- Un solo broker con replicación 1 en producción; TLS y ACL por tópico quedan diferidos (T-I20).

## Redis

| Uso | Dónde | Evidencia |
|-----|-------|-----------|
| Rate limiting del gateway | api-gateway | `RequestRateLimiter` en las rutas |
| Lista negra de access tokens por `jti` (revocación inmediata) | api-gateway y auth-service | `RedisRevokedAccessTokens`, T-B21 |
| Pub/Sub de mensajes y de "escribiendo" por sala | chat-service | `convertAndSend` en `RedisCacheAdapter` |
| Caché de uso general | auth-service, archive-service | adaptadores Redis |

## Reacciones del stream (estado real)

El diseño original preveía Redis Pub/Sub para repartir las reacciones en tiempo real. **La implementación actual no lo usa**:

- `POST` de reacción: `StreamingServiceImpl.addReaction` valida que el stream esté en vivo y con reacciones habilitadas, persiste la reacción en Mongo (`ReactionMongoAdapter`, puerto `ReactionRepository`), incrementa `likesCount` y publica el evento por Kafka (`KafkaStreamingEventPublisher`, tópico `streaming-events`).
- WebSocket `/ws/streaming/{streamId}`: `StreamingWebSocketHandler` reenvía un frame `REACTION` recibido a las sesiones abiertas de ese stream, solo en memoria de la instancia; ese camino no persiste ni publica en Kafka, y el camino REST no llama a ese reenvío.
- Entrega en vivo a todos los espectadores a partir de reacciones persistidas por REST (fan-out entre instancias): pendiente, registrado como **T-B32** (P1, 🟡 RFC: depende del contrato de reacciones y de T-C05). Afecta también a los mensajes de chat del stream.

Fuente: backend/api-gateway/src/main/resources/application.yml, infrastructure/caddy/Caddyfile, docs/decisions/adr-006, adr-007, adr-015, adr-016, docs/api/CONVENTIONS.md §7.

# Datos y almacenamiento

## MongoDB

- Autoalojado en el VPS, con autenticación y **sin puerto público**. Límite de memoria de 1 GiB (caché WiredTiger de 0,4 GB).
- Los scripts de inicialización viven en `infrastructure/docker/mongodb/init-scripts/` (índices declarados, por ejemplo los de `listEvents` en ADR-014).
- Todos los servicios usan hoy la misma credencial de administración. **Un usuario de Mongo por servicio** es la tarea T-I19, aún READY.

## Backup y restauración

| Aspecto | Valor |
|---------|-------|
| Frecuencia | `mongodump` diario |
| Destino | Cloudflare R2 |
| Retención | 14 días |
| Verificación | Restauración de prueba mensual |
| Runbook | `docs/runbooks/restore.md` |

Es la mitigación principal del punto único de falla que es un solo VPS.

## Objetos (fotos, videos, thumbnails)

- Producción: **Cloudflare R2** (10 GB gratis, egress gratis). Local: **SeaweedFS**. Ambos por API S3; el endpoint se configura por entorno (`S3_ENDPOINT`, `S3_BUCKET`, `S3_PUBLIC_URL`).
- media-service sube y valida (magic bytes, MIME de CONVENTIONS §8, límite de 10 MB, re-codificación y retiro de metadatos). transcode-service procesa, y mosaic-service solo guarda el ítem con el `mediaId` (flujo de dos pasos del ADR-016, que se completa con la Ola 3 aún en curso).
- La subida al mosaico en producción está detrás de la propiedad `MOSAIC_UPLOADS_ENABLED` (cerrada por defecto; excepción A3).

Fuente: docker-compose.prod.yml, docs/decisions/adr-011, adr-014, adr-016, docs/roadmap/tasks.md (T-I09, T-I10, T-I19, T-B19, T-B25, T-I21).

# Despliegue

## Fase Tracción (vigente)

| Tema | Decisión (ADR-011) |
|------|--------------------|
| Cómputo | 1 VPS Contabo, 12 GB y 6 vCPU, región EE. UU. |
| Orquestación | Docker Compose (`docker-compose.prod.yml`), proyecto `vito` en `/opt/vito` |
| Borde | Caddy con Let's Encrypt; cabeceras de seguridad en `api.` y `live.` |
| Frontend web | Cloudflare Pages, compilado en GitHub Actions y subido con `wrangler` (direct upload) |
| DNS y dominio | Cloudflare |
| Observabilidad | Grafana Cloud plan gratuito (métricas, logs, trazas OTel) más monitor de uptime |
| Secretos | GitHub Environments y `.env` en el VPS (`chmod 600`, fuera del repo) |
| Imágenes | Sin registro; tag = SHA; `docker save` por SSH y `docker load` |
| Staging | No permanente; CI y Compose local |

## Memoria (tope por contenedor)

| Grupo | MiB |
|-------|----:|
| 10 servicios Java x 512 | 5.120 |
| mongodb | 1.024 |
| kafka | 768 |
| transcode-service | 768 |
| moderation-service | 512 |
| srs | 384 |
| mongo-backup | 384 |
| redis | 256 |
| caddy | 128 |
| **Total** | **9.344** (de ~11,7 GiB del VPS) |

## Pipeline

1. Un PR de release `develop` a `main` (viernes) dispara `cd.yml`.
2. El job *Changes* decide qué servicios reconstruir; *Build* los construye y escanea con Trivy (un CVE HIGH/CRITICAL con arreglo y sin excepción corta el release).
3. *Deploy* espera la aprobación del Environment `production` (otro lead). Hay un Environment `production-hotfix` para hotfix.
4. Por SSH se copia el bundle y las imágenes y se corre `deploy.sh apply`; si la salud falla, vuelve solo al release anterior.
5. Al éxito se crea el tag `v<VERSION>`.

`rollback.yml` vuelve a un release anterior y `expire-approvals.yml` cancela aprobaciones de más de 24 h. Detalle en `docs/runbooks/despliegue.md`.

## Qué queda para la Fase Crecimiento

| Disparador | Paso |
|------------|------|
| Usuarios de iPhone piden la app | Apple Developer |
| Mongo > ~5 GB o la pérdida de 24 h deja de ser aceptable | MongoDB Atlas |
| RAM del VPS > 80 % sostenido o se necesita staging | Segundo VPS |
| Eventos > 150 asistentes o streams > 100 espectadores | VPS dedicado para SRS |
| Varios nodos y alta disponibilidad | k3s/Kubernetes + ArgoCD, Kafka replicado, Mongo replica set |
| Regla general | Los ingresos del mes cubren 2 veces el costo del siguiente nivel |

También diferidos: registro de imágenes (T-I24), Kafka con TLS y ACL (T-I20), staging permanente.

Fuente: docs/decisions/adr-011-traction-stack.md, docker-compose.prod.yml, .github/workflows/cd.yml, docs/runbooks/despliegue.md, .specify/memory/constitution.md (Perfiles de Despliegue).

# Seguridad y autenticación

## Autenticación (ADR-004)

- `POST /api/v1/auth/register|login|refresh` devuelven `{ accessToken, refreshToken, expiresIn, user }`; `user` es plano (`id`, `username`, `email`, `displayName`, `avatar`) y no hay `tokenType` (siempre Bearer).
- `expiresIn` va en segundos. El `refreshToken` viaja solo en el cuerpo de `/auth/refresh`.
- Ante un 401 el cliente llama a refresh y reintenta; si falla, limpia tokens y va a login. `logout` responde 204.
- Los tokens se firman hoy con **HS256** y un secreto compartido (`JWT_SECRET`). La firma asimétrica con JWKS es la tarea T-B30 (bloqueada: requiere spec propio).

## Confianza en el gateway (ADR-007)

| Regla | Detalle |
|-------|---------|
| Solo el gateway añade `X-User-*` | Los borra de todo request entrante |
| Los servicios no confían en el cliente | Ignoran `X-User-*` si no vienen del gateway |
| Llamadas directas (tests, curl) | Deben simular los headers |

## Propiedad de cuentas (ADR-013)

Todas las cuentas de proveedor pertenecen a un correo de equipo; ambos leads son administradores; hay un gestor de contraseñas compartido con 2FA obligatorio, una sola tarjeta de pago y un registro de gastos mensual. Si un lead se va, se rotan credenciales y secretos.

## Controles implementados (según el backlog)

| Control | Tarea | Estado |
|---------|-------|--------|
| Borrado de `X-User-*` entrantes y rate limit por IP | T-B01 | Hecha |
| Rate limit por usuario | T-B23 | Hecha |
| Revocación de access tokens por `jti` | T-B21 | Hecha |
| WS con `?token=` y cierre `4401` | T-B04 | Hecha |
| Validación de subidas (magic bytes, MIME, 10 MB) | T-B19 | Hecha |
| Re-codificación de imágenes y retiro de metadatos | T-B25 | Hecha |
| Cabeceras de seguridad en Caddy | T-I18 | Hecha |
| Escaneo Trivy y gitleaks en CI/CD | CI, CD | Activos |
| Throttling de login por cuenta | T-B22 | RFC |
| Audit log de eventos de seguridad | T-B24 | READY |
| Usuario de Mongo por servicio | T-I19 | READY |

El repositorio es **público** desde el 2026-10-19: ninguna IP, correo de cuenta ni valor de `.env` vive en él.

Fuente: docs/decisions/adr-004, adr-007, adr-013, adr-015, docs/roadmap/tasks.md, backend/SECURITY_CHECKLIST.md.

# Convenciones de contrato

El contrato (`docs/api/openapi.yaml` y `asyncapi.yaml`) es la fuente de verdad; ante una contradicción código/contrato **gana el contrato** (ADR-010). Nadie implementa un endpoint que no esté en él. Resumen de `docs/api/CONVENTIONS.md`:

| Tema | Regla | ADR |
|------|-------|-----|
| Objeto | JSON plano, sin envoltorio `data` | CONVENTIONS §3.1 |
| Lista | Array plano, sin `total` | ADR-002 |
| Paginación | `page` base 0 (default 0), `size` default 20 y máximo 100; orden por defecto `createdAt` descendente | ADR-008 |
| Contador total | Solo como campo propio del endpoint (por ejemplo `stats`), nunca en un envelope genérico | ADR-008 |
| Error | `{ error, message, status, timestamp, details? }`, `message` en es-ES, `timestamp` ISO-8601 UTC | ADR-003 |
| Códigos de error | `VALIDATION_ERROR`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `INTERNAL_ERROR`, `SERVICE_UNAVAILABLE` | ADR-003 |
| Creación | `POST` que crea recurso: 201; acción: 200; `DELETE`: 204 | ADR-005 |
| Idempotencia | Operación idempotente repetida: 200, no 409 | ADR-005 |
| Validación vs conflicto | 400 = request mal formado; 409 = válido pero en conflicto con el estado (duplicados, cupo lleno) | ADR-009 |
| Evento | `title` + `details` + `settings` + `stats` + `status`; nada de `name`, `date` ni `isPublic` en la raíz | ADR-001 |
| Listado de eventos | `GET /events?scope=mine` o `upcoming`; por defecto `mine` | ADR-014 |
| Fechas | ISO-8601 UTC en REST, WS y Kafka | CONVENTIONS §6 |
| Código backend | Es referencia, no especificación | ADR-010 |

Además, todo endpoint nuevo lleva `operationId`, ejemplos, `ErrorResponse` en 4xx/5xx y un fixture en `docs/api/fixtures/`. El job de CI "Contract" ejecuta Spectral y valida los fixtures.

Fuente: docs/api/CONVENTIONS.md, docs/decisions/adr-001, 002, 003, 005, 008, 009, 010, 014.

# Stack y código compartido

## Stack vigente

| Capa | Tecnología |
|------|------------|
| Frontend | Flutter (Dart), Clean Architecture por feature, BLoC |
| Backend | Java con Spring Boot, arquitectura hexagonal. Vigente en `main`: Spring Boot 3.2.12 (10 servicios); destino: 4.0.9 vía T-B31 (ver ADR-018) |
| Servicios de procesamiento | Python FastAPI (transcode, moderation; recommendation diferido) |
| Datos | MongoDB 7, Redis 7, Kafka (KRaft en producción) |
| Streaming | SRS 5 (WebRTC y RTMP), sin transcodificar |
| Objetos | Cloudflare R2 (producción), SeaweedFS (local) |
| Borde | Caddy; Spring Cloud Gateway |
| CI/CD | GitHub Actions |
| Observabilidad | Actuator y Micrometer (Prometheus), logs JSON, OpenTelemetry, Grafana Cloud (decidido en ADR-011; [Pendiente: T-I13]) |

## Spring Boot 4 (ADR-018, Propuesta)

El ADR-018 propone llevar los 10 servicios a **Spring Boot 4.0.9** (Spring Framework 7.0.10, Spring Security 7.0.8, Jackson 3.1.7), **Spring Cloud 2025.1.4** (Gateway 5.0.4) y **Java 21.0.13+9 Temurin** como mínimo. Motivo: Spring Boot 3.2 ya no tiene soporte OSS y los CVE críticos de `spring-webmvc` y `spring-webflux` solo se corrigen en Spring Framework 7; la excepción A9 de Trivy vence el 2026-11-07 y desde el 2026-11-08 Trivy vuelve a bloquear el despliegue.

| Aspecto | Estado |
|---------|--------|
| ADR-018 | Propuesta: falta aprobación de un CODEOWNER |
| Constitución, Principio X ("Spring Boot 3.x LTS") | Requiere enmienda para ser coherente |
| Tarea | T-B31, en curso en la rama `backend/t-b31-spring-boot-soporte` |
| Riesgo | El soporte OSS de la línea 4.0 terminaría el 2026-12-31 según fuentes secundarias, no verificado contra la página oficial |

**Versión vigente:** en `main` los 10 servicios usan Spring Boot **3.2.12**. **Destino:** 4.0.9, que la rama `backend/t-b31-spring-boot-soporte` (T-B31, en curso) aplica a los 10 servicios. ADR-018 sigue en estado Propuesta; este documento describe el destino y no lo presenta como ya desplegado.

## Código Java compartido (ADR-017)

- Hoy el `JwtAuthenticationFilter` está copiado en 8 módulos, y el `GlobalExceptionHandler` y `ErrorResponse` se copiaron a los servicios que no los tenían (T-B03, hecha).
- Es una **desviación temporal** del Principio II con fecha de salida: la tarea **T-B18** crea un parent POM con el módulo `backend/shared-web` y borra las copias.
- T-B18 está bloqueada hasta la implementación de la Ola 1. Mientras tanto no se copia ningún otro tipo de código entre servicios.
- A la fecha no existe la carpeta `backend/shared-web`.

Fuente: docs/decisions/adr-018-spring-boot-4.md, adr-017-shared-java-code.md, docs/roadmap/tasks.md (T-B03, T-B18, T-B31).

# Ramas y releases

Resumen de ADR-012.

| Rama o flujo | Regla |
|--------------|-------|
| `develop` | Integración. Todo PR de feature (`contract/`, `frontend/`, `backend/`, `docs/`) apunta aquí; merge con squash |
| `main` | Producción. Solo recibe PR de release o de hotfix; cada commit que entra se despliega |
| Release semanal | Los viernes, tras la review de integración, un lead abre `release: AAAA-MM-DD` de `develop` a `main`; requiere CI verde y la aprobación de 1 CODEOWNER distinto del autor; merge commit, no squash |
| Tag | `vMAJOR.MINOR.PATCH` sobre `main` al mergear el release (pre-1.0: `v0.MINOR.PATCH`) |
| Hotfix | `hotfix/<nombre>` desde `main`, PR a `main` y luego PR de `main` a `develop` |
| Release vacío | Si `develop` no cambió, no se abre PR |

Producción exige **dos aprobaciones**: la del PR de release y la de un lead en el Environment `production` (constitución v1.3.0, Principio VIII). El CI comprueba que `VERSION` sea mayor que el último tag.

**Commits:** `<tipo>(<ámbito>): <imperativo>`; tipos `feat|fix|docs|refactor|test|chore|ci|contract`.

**Ciclo de trabajo por tarea:** rama `<pista>/<id>-<slug>` desde `develop`, marcar la tarea EN CURSO en `tasks.md`, implementar solo el "Qué", verificar el "Cierre" y abrir un PR a `develop` con "Closes T-xx".

Fuente: docs/decisions/adr-012-branching-and-release.md, docs/handoff/AGENT-BRIEF.md §7, .github/workflows/ci.yml.

# Estado real del proyecto

Fotografía al 2026-10-30, contrastada con `docs/roadmap/tasks.md` y el repo. La sección 6 del AGENT-BRIEF está fechada el 2026-10-16 y varias de sus filas ya no son ciertas; aquí se usa el backlog y el código actuales.

## Lo que ya está resuelto

| Tema | Estado |
|------|--------|
| Contrato REST con fixtures, mock Prism y job "Contract" en CI | Hecho (T-C01 a T-C04, T-C06, T-C08) |
| Gateway: ruta `/ws/**`, `?token=`, cierre 4401, errores ADR-003, rate limit | Hecho (T-B01, T-B02, T-B04, T-B11, T-B23) |
| mosaic y streaming con WS crudo registrado | Hecho (T-B05) |
| Envelope ADR-003 en los servicios sin handler | Hecho (T-B03) |
| Actuator, health y métricas Prometheus | Hecho (T-B14) |
| FastAPI en `docker-compose.yml` | Hecho (T-B09) |
| `docker-compose.prod.yml`, Caddy y límites de memoria | Hecho (T-I07) |
| Backup diario de Mongo a R2 | Hecho (T-I09) |
| media-service con S3 configurable | Hecho (T-I10) |
| Cuentas y VPS aprovisionados | Hecho (T-I06) |
| Protección de rama en `main` y `develop` | Hecho (T-I16) |
| AuthBloc real y WS clients con `?token=` | Hecho (T-F01, T-F04) |

## En curso

| Tarea | Estado |
|-------|--------|
| T-C07 Ola 3 (mosaico), ramas `contract/ola-N` (1 a 6) | En curso (`contract/ola-3`) |
| T-B31 Spring Boot con soporte (ADR-018) | En curso |
| T-I08 Pipeline de despliegue por SSH | Despliegue verificado; falta ensayar el rollback |
| T-I11 Frontend web en Cloudflare Pages | En curso; falta verificar en producción |
| T-I14 Runbooks | En curso; falta el ensayo manual |

## Pendiente o bloqueado

| Tema | Estado |
|------|--------|
| `asyncapi.yaml` v0 (T-C05) | RFC |
| Chat a WS crudo (T-B06) | Bloqueada por T-C05 |
| Grafana Cloud (T-I13) | READY, sin hacer |
| `shared-web` (T-B18) | Bloqueada hasta la Ola 1 |
| JWT asimétrico (T-B30) | Bloqueada: requiere spec |
| CI incremental (T-I12) | READY |
| Usuario de Mongo por servicio (T-I19) | READY |
| Recomendaciones sin servicio en producción (T-I23) | READY |
| Prueba de `mobile_scanner` y `share_plus` en dispositivo (T-F09) | READY, manual, P0 |
| Boolean `is*` (T-B13), `MockApiService` (T-F02) | Bloqueadas por las olas 1 a 5 |
| Consumidores de Kafka | No existen |

La versión del repo es 0.1.1 (archivo `VERSION`).

Fuente: docs/roadmap/tasks.md, docs/handoff/AGENT-BRIEF.md §6, docs/decisions/adr-016-domain-owners.md, VERSION.

# Anexo A. Índice de ADR

| # | Título | Estado | Fecha |
|---|--------|--------|-------|
| 001 | Estructura del recurso Event: `title`, `details`, `settings`, `stats` | Aceptada | 2026-09-25 |
| 002 | Las listas se devuelven como array plano | Aceptada | 2026-09-25 |
| 003 | Envelope de error único `{error,message,status,timestamp}` | Aceptada | 2026-09-25 |
| 004 | `AuthResponse` con tokens y ciclo de refresh unificado | Aceptada | 2026-09-25 |
| 005 | Códigos HTTP de creación, borrado y contenido | Aceptada | 2026-09-25 |
| 006 | WebSocket crudo, no STOMP | Aceptada | 2026-09-25 |
| 007 | Headers `X-User-*` inyectados por el gateway | Aceptada | 2026-09-25 |
| 008 | Paginación `page`/`size` sin `total` | Aceptada | 2026-09-25 |
| 009 | `409` para duplicados, `400` para validación | Aceptada | 2026-09-25 |
| 010 | El código backend existente es referencia, no fuente de verdad | Aceptada | 2026-09-25 |
| 011 | Stack de despliegue de la Fase Tracción | Aceptada | 2026-10-17 |
| 012 | Ramas, release semanal y promoción a producción | Aceptada | 2026-10-17 |
| 013 | Titularidad de cuentas, credenciales y medio de pago | Aceptada | 2026-10-17 |
| 014 | Listado de eventos con `GET /events?scope=` | Aceptada | 2026-10-19 |
| 015 | Identidad en el WS de chat sin `?userId=` | Aceptada | 2026-10-20 |
| 016 | Un solo servicio dueño por capacidad duplicada | Aceptada | 2026-10-22 |
| 017 | Código Java compartido: copia temporal, `shared-web` después | Aceptada | 2026-10-22 |
| 018 | Spring Boot 4.0.x y Java 21 parcheado | Propuesta | 2026-10-29 |

El ADR-018 aún no figura en la tabla de `docs/decisions/README.md`.

Fuente: docs/decisions/README.md y cada ADR.

# Anexo B. Fuentes consultadas

- `docs/handoff/AGENT-BRIEF.md`, `docs/pdf/README.md`, `.claude/agents/doc-pdf.md`
- `docs/decisions/adr-001` a `adr-018` y `README.md`
- `docs/api/CONVENTIONS.md`, `docs/api/asyncapi.yaml` (cabecera), `docs/roadmap/tasks.md`
- `.specify/memory/constitution.md` (Perfiles de Despliegue)
- `docker-compose.yml`, `docker-compose.prod.yml`, `infrastructure/caddy/Caddyfile`
- `backend/*/src/main/resources/application.yml`, `ai-services/*/Dockerfile`
- `.github/workflows/ci.yml`, `cd.yml`, `docs/runbooks/despliegue.md`
