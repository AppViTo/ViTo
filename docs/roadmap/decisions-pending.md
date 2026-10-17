# Decisiones pendientes (D-xx)

Cada decisión la toman los leads. Se cierra con un ADR (`docs/decisions/adr-NNN-*.md`,
rama `contract/<nombre>` si afecta el contrato, `docs/<nombre>` si no) y se marca aquí
como ✅ con el número de ADR. Las tareas bloqueadas en `tasks.md` citan el D-xx.

Las opciones listadas son las que salieron del análisis del 2026-10-16. **La columna
"Propuesta" es una recomendación, no una decisión.**

| ID | Decisión | Bloquea | Propuesta | Estado |
|----|----------|---------|-----------|--------|
| D-01 | Dueño de cada dominio duplicado | Olas 1–4 | ver §D-01 | ⏳ |
| D-02 | Stack de despliegue y herramientas por costo | Etapa 5, O-11/12/13/17 | ver §D-02 | ⏳ |
| D-03 | Semántica del listado de eventos (`GET /events`) | T-C06, T-B07, T-F08 | ver §D-03 | ⏳ |
| D-04 | Mensajería durable del MVP (Kafka vs alternativa) | Ola 3, O-16/O-17 | ver §D-02.b | ⏳ |
| D-05 | Estrategia de código compartido Java (`shared-*`) | T-B03, O-23 | ver §D-05 | ⏳ |
| D-06 | Identidad en el WS de chat (`?userId=`) | T-B06, T-F04 (parte chat) | ver §D-06 | ⏳ |
| D-07 | Estrategia de ramas (`develop` vs `main`) | O-24 | ver §D-07 | ⏳ |
| D-08 | Aprobación del triage de `endpoint-inventory.md` | Olas 1–6 | aprobar por ola | ⏳ |

---

## D-01 — Dueño de dominios duplicados

Hoy la misma capacidad vive en dos servicios:

| Capacidad | Implementación A | Implementación B | Propuesta |
|-----------|------------------|------------------|-----------|
| QR + check-in | `event-service`: `POST /events/{id}/check-in`, `GET /events/{id}/qr-code` | `checkin-service`: `/checkin/ticket`, `/checkin/validate`, stats, recent | **checkin-service** es dueño. Se retiran las dos rutas de event-service. checkin consulta capacidad/estado del evento por API o evento Kafka. |
| Subida de medios | `media-service`: `POST /media/upload` (en contrato) | `mosaic-service`: `POST /mosaic/photo` | **media-service** sube y guarda; mosaic solo crea el ítem con `mediaId` (`POST /mosaic/post`). Se retira `/mosaic/photo`. |
| Ítem de check-in en el mosaico | `mosaic-service`: `POST /mosaic/checkin` (público) | — | **Interno**: lo crea mosaic al consumir el evento de check-in. No se enruta en gateway. |
| Chat | `chat-service` (salas, mensajes) | `streaming-service`: `POST/GET /streaming/{id}/chat` | **chat-service** es dueño; el stream usa una sala de chat ligada al `streamId`. Se retira el chat de streaming. |
| Info de stream | `GET /streaming/{id}` | `GET /streaming/{id}/info` | Fusionar en `GET /streaming/{id}`. |
| Invitación vs RSVP | `POST /events/{id}/invitations/respond` | `POST /events/{id}/rsvp` | Mantener ambos solo si la semántica difiere (aceptar invitación privada vs RSVP público). Definir en Ola 1. |

## D-02 — Stack de despliegue y costo

La constitución v1.1.0 marca estas reglas como **en revisión** (no se implementan ni se
eliminan hasta cerrar esta decisión). Sub-decisiones:

| # | Hoy exige | Alternativas a evaluar |
|---|-----------|------------------------|
| a | Kubernetes + ArgoCD + Kustomize overlays dev/staging/prod, HPA, PDB, NetworkPolicy | VM única con Docker Compose (staging) · k3s en 1 nodo · k8s gestionado solo para prod post-MVP |
| b | Kafka KRaft, replicación ≥ 3 | 1 broker KRaft en staging · Redpanda · Redis Streams para el MVP (Kafka después) |
| c | MongoDB Atlas replica set + backups diarios probados | Atlas M0/Flex en staging · Mongo autoalojado + `mongodump` programado |
| d | 10 microservicios Java + 3 Python desplegables | Mantener · Fusionar por afinidad (event+checkin, media+mosaic+archive) según Principio X |
| e | AWS S3 / MinIO (compose ya usa **SeaweedFS**) | SeaweedFS · MinIO · Cloudflare R2 (sin egress) · S3 |
| f | Redis-compatible: AGENTS dice Dragonfly, código usa Redis 7.2 | Redis · Dragonfly · Valkey |
| g | Service discovery: AGENTS dice Eureka, código usa rutas estáticas | DNS de la plataforma (sin Eureka) · Eureka |
| h | Prometheus + Grafana + OTel con dashboards y alertas como código | Grafana Cloud free tier · stack autoalojado mínimo |
| i | Gestor de secretos (Sealed Secrets / AWS Secrets Manager) | GitHub Environments secrets · Sealed Secrets · Doppler free |
| j | Registro de imágenes GHCR (plan Free: cuota de almacenamiento privado) | GHCR con retención · Docker Hub · registro del proveedor cloud |
| k | Streaming SRS / Ant Media | SRS autoalojado 1 nodo (Ant Media fuera del MVP) |

**Entrada requerida de los leads:** presupuesto mensual objetivo para staging y para
producción del MVP.

## D-03 — Listado de eventos

El contrato publica `GET /api/v1/events` (`listEvents`) y el frontend lo usa como
"mis eventos" (`event_remote_datasource.dart:147`). El backend no lo implementa: solo
tiene `GET /events/my-events` y `GET /events/upcoming` → hoy responde 405.

| Opción | Efecto |
|--------|--------|
| A (propuesta) | `GET /events?scope=mine\|upcoming\|public&page&size`. Backend fusiona `/my-events` y `/upcoming` en uno. Front cambia `queryParameters`. |
| B | Contrato pasa a `GET /events/my-events` y `GET /events/upcoming`; se retira `listEvents`. |

## D-05 — Código compartido Java

La constitución (Principio II) exige extraer a librería `backend/shared-*` el
`JwtAuthenticationFilter` (duplicado en 7 servicios) y el `ErrorResponse`/handler de
ADR-003. Hoy cada servicio se construye aislado (contexto Docker = carpeta del servicio,
sin parent POM).

| Opción | Efecto |
|--------|--------|
| A (propuesta) | Parent POM Maven multi-módulo en `backend/` con `shared-web`; Dockerfile con contexto `backend/`. Cambia CI y `backend/Dockerfile`. |
| B | Librería publicada en GitHub Packages; cada servicio la consume por versión. |
| C (temporal) | Copiar el handler de auth-service a los 6 servicios que faltan y registrar la deuda en *Complexity Tracking*. |

## D-06 — Identidad en WS de chat

`CONVENTIONS.md` §7 publica `/ws/chat?userId=<id>&token=<jwt>`. El `userId` en query lo
elige el cliente (suplantable) y es redundante con el token. **Propuesta:** retirar
`userId`; el servidor toma la identidad del token validado por el gateway. Cambio de
contrato → RFC.

## D-07 — Ramas

`CONTRIBUTING.md` define `develop` como integración, pero los PR recientes (#12, #13)
van a `main` y `deploy-frontend.yml` despliega desde `main`. **Propuesta:** mantener
`develop` como destino de PR de feature y promover a `main` con un PR de release
semanal (viernes), que dispara el deploy.

## D-08 — Triage de endpoints

Aprobar (o corregir) la columna "Decisión propuesta" de `endpoint-inventory.md`, **ola
por ola**. Aprobar la ola N desbloquea su rama `contract/<ola>`.
