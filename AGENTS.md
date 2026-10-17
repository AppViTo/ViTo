# AGENTS.md - ViTo (VibeTogether)

## VISIÓN DEL PROYECTO

ViTo es una plataforma de **ciclo de vida de eventos sociales** que integra:
- Creación de eventos + invitaciones digitales
- RSVP con opciones múltiples + generación de QR
- Mosaico en vivo (grid + feed) durante el evento
- Streaming unidireccional con reacciones en tiempo real
- Archivo post-evento con opción de compartir

## LEER PRIMERO (todo agente, antes de escribir código)

1. **`docs/handoff/AGENT-BRIEF.md`** — contexto completo, estado real del repo, reglas y ciclo de trabajo.
2. **`docs/roadmap/tasks.md`** — backlog único. Solo se implementan tareas en estado 🟢 `READY`.
3. **`.specify/memory/constitution.md`** — prevalece sobre este archivo. Jerarquía:
   constitución > ADR > CONVENTIONS > contrato > CONTRIBUTING > `docs/roadmap/` > AGENTS > código.

> **Aviso de vigencia (2026-10-16):** la tabla de stack y la estructura de carpetas de
> abajo describen el **objetivo**, no el estado actual. Diferencias conocidas: el código
> usa Redis 7.2 (no Dragonfly), rutas estáticas (no Eureka), SeaweedFS (no S3/MinIO) y
> solo Docker Compose; existen además `checkin-service`, `mosaic-service` y
> `archive-service`. Kubernetes/ArgoCD, Kafka, Atlas, almacenamiento y observabilidad
> están **en revisión por costo** (decisión D-02 en `docs/roadmap/decisions-pending.md`).
> El estado real está en la sección 6 del AGENT-BRIEF.

## STACK TECNOLÓGICO

| Capa | Tecnología |
|------|------------|
| Frontend | Flutter (Dart) - Clean Architecture Feature-First |
| Backend Core | Java Spring Boot - Arquitectura Hexagonal | Java Spring Boot - Arquitectura - EDD| 
| Microservicios IA/Transcode | Python FastAPI | Sprint Security|
| Base de Datos | MongoDB (NoSQL) |
| Cache/Pub-Sub | dragonFly|
| Message Broker | Apache Kafka |
| API Gateway | Spring Cloud Gateway |
| API discovery | Sprit cloud eureka|
| Streaming Media | SRS / Ant Media Server (WebRTC/RTMP) |
| Object Storage | AWS S3 / MinIO |
| Container | Docker + Docker Compose |
| Orquestación | Kubernetes (producción) | ArgoCD|
| CI/CD | GitHub Actions |
| Observavility | logs SLF4J | opentelemtry | Sprint Boot Actuator(Micrometer) | prometheus o Grafana|

## ESTRUCTURA DE CARPETAS

```
vito/
├── AGENTS.md
├── README.md
├── CONTRIBUTING.md                    # Reglas de colaboración entre equipos
├── docker-compose.yml
├── .github/
│   ├── CODEOWNERS                     # Dueños de cada área (basta 1 aprobación de un dueño)
│   ├── pull_request_template.md
│   ├── ISSUE_TEMPLATE/
│   │   └── contract-change.md         # RFC para cambiar el contrato
│   └── workflows/
│       ├── ci.yml
│       └── cd.yml
│
├── backend/
│   ├── auth-service/                    # Spring Boot - Autenticación
│   │   ├── src/main/java/com/vito/auth/
│   │   │   ├── AuthApplication.java
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   ├── port/
│   │   │   │   └── exception/
│   │   │   ├── application/
│   │   │   │   ├── service/
│   │   │   │   └── dto/
│   │   │   ├── infrastructure/
│   │   │   │   ├── config/
│   │   │   │   ├── adapter/
│   │   │   │   └── mapper/
│   │   │   └── shared/
│   │   ├── src/main/resources/
│   │   └── pom.xml
│   │
│   ├── social-service/                  # Spring Boot - Grafo Social
│   │   ├── src/main/java/com/vito/social/
│   │   │   ├── SocialApplication.java
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   ├── infrastructure/
│   │   │   └── shared/
│   │   ├── src/main/resources/
│   │   └── pom.xml
│   │
│   ├── media-service/                   # Spring Boot - Multimedia
│   │   ├── src/main/java/com/vito/media/
│   │   │   ├── MediaApplication.java
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   ├── infrastructure/
│   │   │   └── shared/
│   │   ├── src/main/resources/
│   │   └── pom.xml
│   │
│   ├── event-service/                   # Spring Boot - Eventos, invitaciones y RSVP
│   │   ├── src/main/java/com/vito/event/
│   │   │   ├── EventServiceApplication.java
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── Event.java
│   │   │   │   │   ├── Invitation.java
│   │   │   │   │   ├── RSVP.java
│   │   │   │   │   └── QRTicket.java
│   │   │   │   ├── port/
│   │   │   │   └── exception/
│   │   │   ├── application/
│   │   │   │   ├── service/
│   │   │   │   └── dto/
│   │   │   ├── infrastructure/
│   │   │   │   ├── config/
│   │   │   │   ├── adapter/
│   │   │   │   └── mapper/
│   │   │   └── shared/
│   │   ├── src/main/resources/
│   │   └── pom.xml
│   │
│   ├── chat-service/                    # Spring Boot - Chat WebSocket
│   │   ├── src/main/java/com/vito/chat/
│   │   │   ├── ChatApplication.java
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   ├── infrastructure/
│   │   │   └── shared/
│   │   ├── src/main/resources/
│   │   └── pom.xml
│   │
│   ├── streaming-service/               # Spring Boot - Streaming Live
│   │   ├── src/main/java/com/vito/streaming/
│   │   │   ├── StreamingApplication.java
│   │   │   ├── domain/
│   │   │   ├── application/
│   │   │   ├── infrastructure/
│   │   │   └── shared/
│   │   ├── src/main/resources/
│   │   └── pom.xml
│   │
│   └── api-gateway/                     # Spring Cloud Gateway
│       ├── src/main/java/com/vito/gateway/
│       │   └── GatewayApplication.java
│       ├── src/main/resources/
│       └── pom.xml
│
├── ai-services/
│   ├── transcode-service/               # FastAPI - Transcodificación
│   │   ├── app/
│   │   │   ├── main.py
│   │   │   ├── worker.py
│   │   │   ├── tasks/
│   │   │   ├── services/
│   │   │   └── schemas/
│   │   ├── requirements.txt
│   │   └── Dockerfile
│   │
│   ├── recommendation-service/          # FastAPI - Recomendaciones IA
│   │   ├── app/
│   │   │   ├── main.py
│   │   │   ├── services/
│   │   │   └── api/
│   │   ├── requirements.txt
│   │   └── Dockerfile
│   │
│   └── moderation-service/              # FastAPI - Moderación Contenido
│       ├── app/
│       │   ├── main.py
│       │   ├── services/
│       │   └── api/
│       ├── requirements.txt
│       └── Dockerfile
│
├── frontend/
│   └── vito_app/                        # Flutter Application
│       ├── lib/
│       │   ├── main.dart
│       │   ├── app.dart
│       │   ├── core/
│       │   │   ├── error/
│       │   │   ├── network/
│       │   │   ├── usecase/
│       │   │   ├── util/
│       │   │   └── di/
│       │   ├── features/
│       │   │   ├── auth/
│       │   │   │   ├── data/
│       │   │   │   ├── domain/
│       │   │   │   └── presentation/
│       │   │   ├── social/
│       │   │   │   ├── data/
│       │   │   │   ├── domain/
│       │   │   │   └── presentation/
│       │   │   ├── events/
│       │   │   │   ├── data/
│       │   │   │   │   ├── datasources/
│       │   │   │   │   │   ├── event_remote_datasource.dart
│       │   │   │   │   │   └── qr_datasource.dart
│       │   │   │   │   ├── models/
│       │   │   │   │   └── repositories/
│       │   │   │   ├── domain/
│       │   │   │   │   ├── entities/
│       │   │   │   │   │   ├── event.dart
│       │   │   │   │   │   ├── invitation.dart
│       │   │   │   │   │   ├── rsvp.dart
│       │   │   │   │   │   └── qr_ticket.dart
│       │   │   │   │   ├── repositories/
│       │   │   │   │   └── usecases/
│       │   │   │   │       ├── create_event.dart
│       │   │   │   │       ├── send_invitation.dart
│       │   │   │   │       ├── respond_rsvp.dart
│       │   │   │   │       ├── generate_qr.dart
│       │   │   │   │       └── validate_checkin.dart
│       │   │   │   └── presentation/
│       │   │   │       ├── bloc/
│       │   │   │       │   ├── event_bloc.dart
│       │   │   │       │   ├── event_event.dart
│       │   │   │       │   └── event_state.dart
│       │   │   │       ├── pages/
│       │   │   │       │   ├── create_event_page.dart
│       │   │   │       │   ├── event_detail_page.dart
│       │   │   │       │   ├── rsvp_response_page.dart
│       │   │   │       │   ├── qr_display_page.dart
│       │   │   │       │   └── qr_scanner_page.dart
│       │   │   │       └── widgets/
│       │   │   │           ├── event_card.dart
│       │   │   │           ├── rsvp_options.dart
│       │   │   │           ├── qr_display.dart
│       │   │   │           └── scanner_overlay.dart
│       │   │   ├── mosaic/                      # Mosaico en Vivo
│       │   │   │   ├── data/
│       │   │   │   ├── domain/
│       │   │   │   └── presentation/
│       │   │   │       ├── bloc/
│       │   │   │       │   ├── mosaic_bloc.dart
│       │   │   │       │   ├── mosaic_event.dart
│       │   │   │       │   └── mosaic_state.dart
│       │   │   │       ├── pages/
│       │   │   │       │   ├── mosaic_page.dart
│       │   │   │       │   └── mosaic_grid_page.dart
│       │   │   │       └── widgets/
│       │   │   │           ├── mosaic_grid.dart
│       │   │   │           ├── mosaic_feed.dart
│       │   │   │           ├── upload_button.dart
│       │   │   │           └── content_card.dart
│       │   │   ├── streaming/
│       │   │   │   ├── data/
│       │   │   │   │   ├── datasources/
│       │   │   │   │   │   ├── stream_remote_datasource.dart
│       │   │   │   │   │   └── webrtc_datasource.dart
│       │   │   │   │   └── repositories/
│       │   │   │   ├── domain/
│       │   │   │   │   ├── entities/
│       │   │   │   │   │   ├── stream_session.dart
│       │   │   │   │   │   └── reaction.dart
│       │   │   │   │   └── usecases/
│       │   │   │   │       ├── start_stream.dart
│       │   │   │   │       ├── join_stream.dart
│       │   │   │   │       └── send_reaction.dart
│       │   │   │   └── presentation/
│       │   │   │       ├── bloc/
│       │   │   │       │   ├── stream_bloc.dart
│       │   │   │       │   └── stream_state.dart
│       │   │   │       ├── pages/
│       │   │   │       │   ├── stream_host_page.dart
│       │   │   │       │   └── stream_viewer_page.dart
│       │   │   │       └── widgets/
│       │   │   │           ├── rtc_video_view.dart
│       │   │   │           ├── viewer_count_badge.dart
│       │   │   │           ├── stream_controls.dart
│       │   │   │           └── reactions_overlay.dart
│       │   │   └── chat/
│       │   │       ├── data/
│       │   │       ├── domain/
│       │   │       └── presentation/
│       │   ├── shared/
│       │   │   ├── widgets/
│       │   │   └── models/
│       │   └── config/
│       │       ├── theme/
│       │       ├── routes/
│       │       └── env/
│       ├── pubspec.yaml
│       └── analysis_options.yaml
│
├── infrastructure/
│   ├── docker/
│   │   ├── mongodb/
│   │   │   └── init-scripts/
│   │   ├── redis/
│   │   │   └── redis.conf
│   │   ├── kafka/
│   │   │   └── docker-compose.yml
│   │   └── srs/
│   │       └── srs.conf
│   ├── kubernetes/
│   │   ├── base/
│   │   └── overlays/
│   └── scripts/
│       ├── setup.sh
│       └── deploy.sh
│
└── docs/
    ├── architecture.md
    ├── api/                           # CONTRATO FUENTE DE VERDAD
    │   ├── openapi.yaml               # REST — 100% de los endpoints que consume el frontend
    │   ├── asyncapi.yaml              # WebSockets (chat, checkin, mosaic, streaming)
    │   ├── CONVENTIONS.md             # Envelopes, errores, auth, enums, paginación
    │   ├── fixtures/                  # Ejemplos ejecutables (mock + tests Dart)
    │   └── README.md                  # OBSOLETO — ver CONVENTIONS.md
    ├── decisions/                     # ADR — decisiones de contrato (vinculantes)
    │   ├── README.md
    │   └── adr-001..010
    ├── handoff/                       # Instrucciones de implementación por feature
    │   └── TEMPLATE.md
    └── diagrams/
```

## REGLAS DE CONTRATO (OBLIGATORIAS PARA TODO AGENTE)

Antes de escribir cualquier endpoint o modificar `docs/api/`, leer:

1. **`CONTRIBUTING.md`** — ramas, commits, proceso RFC, cadencia.
2. **`docs/api/CONVENTIONS.md`** — cómo se formatean las respuestas (array plano, envelope de error único, auth Bearer + headers `X-User-*`, enums, paginación).
3. **`docs/decisions/README.md`** — los ADR ya aprobados; son vinculantes.
4. **`docs/roadmap/endpoint-inventory.md`** — decisión propuesta para cada endpoint del código y ola en la que entra al contrato.

Reglas inquebrables:

- **Nadie implementa un endpoint que no esté en `docs/api/openapi.yaml`** (o `asyncapi.yaml` para WS).
- Si falta algo → issue con template `contract-change` → PR al contrato **aprobado por al menos 1 lead (CODEOWNER)** → solo entonces se implementa.
- Ante contradicción "el código dice X / el contrato dice Y" → **gana el contrato** (`docs/decisions/adr-010`: el backend existente es referencia, no fuente de verdad).
- Todo endpoint nuevo lleva: `operationId`, ejemplos, `ErrorResponse` en 4xx/5xx y fixture en `docs/api/fixtures/`.

## PLAN DE TRABAJO - CRONOGRAMA

### FASE 0: SETUP & INFRAESTRUCTURA (Semanas 0-1)
- Crear repositorio
- Docker Compose local (MongoDB, Redis, Kafka, SRS)
- Setup Spring Boot base (auth-service)
- Setup FastAPI base (transcode-service)
- Setup Flutter con Clean Architecture
- Pipeline CI/CD básico
- Configurar AWS + S3 bucket
- Configurar MongoDB Atlas

### FASE 1: AUTH & USER MANAGEMENT (Semanas 2-3)
- Modelo User + Puerto Repository
- MongoDB Adapter
- Register, Login, Refresh Token
- JWT + Spring Security
- Endpoints REST
- Redis blacklist tokens
- Flutter: BLoC Auth + Login/Register
- Tests unitarios

### FASE 2: SOCIAL GRAPH (Semana 4)
- Modelo Relationships
- Follow/Unfollow/GetFollowers
- Endpoints REST
- Contadores atómicos
- Flutter: Profile + Follow

### FASE 3: EVENTOS & INVITACIONES (Semanas 5-6)
- Modelo Event
- CRUD Eventos
- SendInvitation (app + links externos)
- RSVP: Accept/Decline/Maybe/Waitlist
- Publicación eventos públicos
- Flutter: Create Event + RSVP

### FASE 4: QR & CHECK-IN (Semana 7)
- Generate QR Token (JWT RSA)
- Validate QR (signature + expiry + capacity)
- Control capacidad atómico
- Flutter: QR Display + Scanner

### FASE 5: MOSAICO EN VIVO (Semanas 8-9)
- Upload Content (image/video)
- Presigned URLs S3
- Worker Python: resize/thumbnail
- Kafka: notificación mosaico
- Redis Pub/Sub: tiempo real
- Flutter: Grid + Feed + Auto-refresh

### FASE 6: STREAMING (Semana 10)
- Init Stream + session token
- Integración SRS/Ant Media
- Reacciones vía Redis Pub/Sub
- Flutter: Host + Viewer Pages

### FASE 7: POST-EVENTO (Semana 11)
- End Event + freeze mosaic
- Highlights auto-generados
- Galería archive
- Share link

### FASE 8: POLISH & MVP RELEASE (Semana 12)
- Testing completo
- Bug fixes
- Optimización queries
- Deploy staging
- Documentación API

## ROLES Y RESPONSABILIDADES

| Rol | Responsabilidades | Foco MVP |
|-----|-------------------|----------|
| **Dev 1 (Backend Lead)** | Spring Boot, Kafka, MongoDB, Redis | Auth, Events, QR, Streaming |
| **Dev 2 (Frontend Lead)** | Flutter, UI/UX, state management | Todas las features Flutter |
| **Dev 3 (FullStack/DevOps)** | Python services, infra, CI/CD | Transcode, Docker, AWS |

## DEFINICIÓN DE ÉXITO (MVP)

1. ✅ Auth: Registro + Login + JWT
2. ✅ Social Graph: Follow/Unfollow
3. ✅ Eventos: Crear eventos públicos/privados
4. ✅ Invitaciones: App + links externos
5. ✅ RSVP: Opciones múltiples
6. ✅ QR: Código único por invitado
7. ✅ Check-in: Validación <200ms
8. ✅ Mosaico: Grid + feed tiempo real
9. ✅ Streaming: Host transmite + reacciones
10. ✅ Post-evento: Galería + compartir

## KPIs OBJETIVO

| Métrica | Target |
|---------|--------|
| Conversión invitado → registro | > 70% |
| RSVP completado | > 80% |
| Check-in exitoso QR | > 95% |
| Contenido subido durante evento | > 50% asistentes |
| Latencia mosaico en vivo | < 3s |
| Latencia reacciones stream | < 1s |
| QR validation time | < 200ms |

## COMANDOS ÚTILES

```bash
# Levantar stack completo
docker-compose up -d

# Correr auth-service
cd backend/auth-service && ./mvnw spring-boot:run

# Correr transcode-service
cd ai-services/transcode-service && uvicorn app.main:app --reload

# Correr Flutter
cd frontend/vito_app && flutter run

# Ejecutar tests
cd backend/auth-service && ./mvnw test
cd frontend/vito_app && flutter test
```
