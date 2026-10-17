# AGENT BRIEF — Contexto completo para agentes implementadores

> **Para quién:** cualquier agente (frontend, backend o DevOps) que vaya a implementar
> tareas de [`docs/roadmap/tasks.md`](../roadmap/tasks.md).
> **Léelo completo antes de tomar tu primera tarea.** Vigente desde 2026-10-16.

---

## 1. Qué es ViTo (en 6 líneas)

Plataforma de ciclo de vida de eventos sociales: crear evento → invitar → RSVP → QR →
check-in → mosaico en vivo → streaming con reacciones → archivo post-evento.
Monorepo: Flutter (`frontend/vito_app`), 10 servicios Spring Boot (`backend/*`, gateway
:8080 + servicios :8081–8089), 3 FastAPI (`ai-services/*`), MongoDB, Redis, Kafka, SRS y
SeaweedFS en `docker-compose.yml`. Dos equipos trabajan en paralelo y se integran **solo
a través del contrato**.

## 2. Jerarquía de autoridad (quién gana ante una contradicción)

```
.specify/memory/constitution.md   (principios I–X, observaciones O-xx)
  > docs/decisions/adr-*.md        (ADR-001..010, vinculantes)
  > docs/api/CONVENTIONS.md        (formatos, errores, auth, enums, paginación, WS)
  > docs/api/openapi.yaml / asyncapi.yaml  (el contrato ejecutable)
  > CONTRIBUTING.md                (ramas, commits, RFC, DoD)
  > docs/roadmap/*                 (qué hacer y en qué orden)
  > AGENTS.md
  > código existente               (referencia, NO fuente de verdad — ADR-010)
```

`docs/api/README.md` está **obsoleto**: no lo uses como referencia.

## 3. Orden de lectura obligatorio

1. Este documento.
2. `docs/roadmap/README.md` → `tasks.md` (tu tarea) → `decisions-pending.md`.
3. `docs/api/CONVENTIONS.md` completo (257 líneas).
4. Los ADR que cite tu tarea (`docs/decisions/`).
5. Constitución: al menos los principios que cite tu tarea.

## 4. Reglas inquebrables

1. **Contrato primero.** No implementes, publiques ni consumas un endpoint, frame WS o
   evento Kafka que no esté en `openapi.yaml`/`asyncapi.yaml`. Lo que existe en el
   código y no en el contrato está **no publicado**.
2. **Si el código y el contrato discrepan, gana el contrato.** No "arregles" el contrato
   para que encaje con el código: abre un RFC.
3. **Solo tareas 🟢 READY.** Las 🟡 RFC se redactan pero no se mergean sin aprobación de
   1 CODEOWNER (`.github/CODEOWNERS`). Las 🔴 no se tocan.
4. **No tomes decisiones de la lista D-xx.** Si tu tarea te obliga a elegir algo que está
   en `decisions-pending.md`, detente y repórtalo.
5. **Sin mocks ni datos hardcodeados** en flujos que entregas (Flutter: no
   `MockApiService`, no `// MOCK`). Para desarrollar sin backend usa el mock Prism
   (`npm run mock:api`, cuando T-C02 esté hecha).
6. **Nada de secretos** en el repo ni en logs (ni tokens truncados).
7. **No amplíes el alcance.** Si ves otro problema, anótalo en el PR como "hallazgo" y
   propón una tarea nueva; no lo arregles en el mismo PR.

## 5. Formatos que debes respetar (resumen de CONVENTIONS; ante duda, CONVENTIONS manda)

| Tema | Regla |
|------|-------|
| Base URL | `/api/v1/...` siempre vía gateway `:8080` |
| Auth | `Authorization: Bearer <accessToken>`; el gateway inyecta `X-User-Id`, `X-User-Email`, `X-User-Roles` (CSV). Los servicios **no** confían en esos headers si vienen del cliente (T-B01). |
| Objeto | JSON plano, sin envoltorio `{data: …}` |
| Lista | **array plano** (ADR-002), paginada con `page` (base 0) y `size` (máx. 100), sin `total` (ADR-008) |
| Error | `{ "error": "SCREAMING_SNAKE", "message": "es-ES", "status": 400, "timestamp": "ISO-8601 UTC" }` (ADR-003) |
| Códigos | 201 crear · 204 borrar · 400 validación · 401 sin token · 403 sin permiso · 404 · 409 duplicado (ADR-005, ADR-009) |
| Event | `title` + `details{…}` + `settings{…}` + `stats{…}` + `status` (ADR-001). Nunca `name`/`date`/`isPublic` en la raíz. |
| Fechas | ISO-8601 UTC en payloads REST, WS y Kafka (no epoch) |
| WebSocket | WS **crudo** (no STOMP/SockJS) vía gateway `/ws/...?token=<jwt>`; frames `{ "type": "SCREAMING_SNAKE", "data": {…} }`; token inválido → cierre `4401` (ADR-006) |

## 6. Estado real del repo (2026-10-16) — lo que vas a encontrar

**Lo documentado que NO es cierto todavía** (no lo asumas):

| Documento dice | Realidad |
|----------------|----------|
| `asyncapi.yaml`, `docs/api/fixtures/`, `npm run mock:api` existen | No existen (T-C01, T-C02, T-C05) |
| El gateway rutea `/ws/**` y valida `?token=` | No hay ruta WS (T-B04) |
| Todos los WS son crudos | chat usa STOMP/SockJS; mosaic y streaming tienen handler pero **no** están registrados (T-B05, T-B06) |
| Todo error sigue ADR-003 | Solo auth, chat y media tienen handler; el gateway responde `{error,status}` (T-B02, T-B03) |
| `GET /api/v1/events` existe | Está en el contrato y el front lo llama, pero el backend no lo implementa → 405 (D-03) |
| Stack: Dragonfly, Eureka, S3/MinIO, Kubernetes+ArgoCD | Redis 7.2, rutas estáticas, SeaweedFS, solo compose. **En revisión por costo (D-02).** |
| Java 21 / Spring Boot soportado | Java 17; Spring Boot 3.2.1 (10 poms) y 3.5.3 (2) |
| CD despliega | `cd.yml` solo construye 2 imágenes (una rota) y el deploy es `echo` |

**Lo que sí funciona:** CI (`ci.yml`) con `mvn clean verify` ×10, pytest de transcode,
`flutter analyze --fatal-infos` + `flutter test`, build de 11 imágenes con
`backend/Dockerfile`; deploy web a Vercel desde `main`; compose `--profile app`.

**Mapa rápido de código:**

| Qué | Dónde |
|-----|-------|
| Gateway (rutas, CORS, rate limit) | `backend/api-gateway/src/main/resources/application.yml` |
| Filtro JWT del gateway | `backend/api-gateway/.../security/JwtAuthenticationFilter.java` |
| Controller de cada servicio | `backend/<svc>/src/main/java/com/vito/<svc>/infrastructure/adapter/inbound/**/*Controller.java` |
| Handlers WS | `backend/<svc>/.../infrastructure/websocket/*WebSocketHandler.java` |
| Handler de errores de referencia | `backend/auth-service/.../inbound/rest/GlobalExceptionHandler.java` |
| DI Flutter | `frontend/vito_app/lib/core/di/injection_container.dart` |
| Rutas Flutter | `frontend/vito_app/lib/config/routes/app_router.dart` |
| Env Flutter (`API_BASE_URL`, `WS_BASE_URL`) | `frontend/vito_app/lib/config/env/env.dart` |
| Interceptor de token | `frontend/vito_app/lib/core/network/token_interceptor.dart` |
| Datasources / repos por feature | `frontend/vito_app/lib/features/<f>/data/` |
| Inventario completo de endpoints | `docs/roadmap/endpoint-inventory.md` |

## 7. Ciclo de trabajo por tarea

```
1. Elegir una tarea 🟢 READY de tasks.md (respeta "tras T-xx").
2. git checkout develop && git pull
   git checkout -b <pista>/<id-en-minúsculas>-<slug>    p.ej. backend/t-b01-strip-user-headers
3. Primer commit: marcar la tarea 🔵 EN CURSO (<rama>) en tasks.md.
4. Implementar SOLO el "Qué". Respetar el "No hacer".
5. Verificar el "Cierre" con comandos reales (sección 8). Pegar la salida en el PR.
6. PR → develop con el template; título Conventional Commit; cuerpo con "Closes T-xx",
   principios de la constitución tocados y, si aplica, observaciones O-xx que cierra.
7. Marcar ✅ HECHA (#PR) en tasks.md dentro del mismo PR.
8. Squash merge tras 1 aprobación de CODEOWNER (distinto del autor).
```

Tareas atómicas (≤ 1 día, sin contrato nuevo) van directo. Las olas de contrato
(T-C07) se ejecutan con Spec Kit: `/speckit-specify` → `/speckit-clarify` →
`/speckit-plan` (con *Constitution Check*) → `/speckit-tasks` → `/speckit-analyze` →
`/speckit-implement`.

**Commits:** `<tipo>(<ámbito>): <imperativo>` — tipos `feat|fix|docs|refactor|test|chore|ci|contract`,
ámbitos `auth|events|checkin|social|mosaic|streaming|chat|archive|media|gateway|api|app`.

## 8. Comandos de verificación

```bash
# Backend (un servicio)
cd backend/<svc> && mvn clean verify

# Stack local completo
docker compose --profile app up -d --build

# Frontend
cd frontend/vito_app && flutter pub get && flutter analyze --fatal-infos && flutter test

# Contrato
npx @stoplight/spectral-cli lint docs/api/openapi.yaml      # hoy: 0 problemas
npx @asyncapi/cli validate docs/api/asyncapi.yaml            # cuando exista
npm run mock:api                                             # cuando exista (T-C02)

# Python
cd ai-services/transcode-service && pytest
```

## 9. Cuándo detenerte y preguntar

- La tarea requiere un endpoint, campo, enum o frame que no está en el contrato.
- La tarea te obliga a elegir una opción de `decisions-pending.md`.
- El "Cierre" no se puede verificar con lo que hay (falta infra, falta otra tarea).
- Encuentras que el contrato o un ADR parecen estar mal.

En esos casos: marca la tarea `⛔ BLOQUEADA (motivo)` en `tasks.md`, describe el
problema en el PR o en un issue (`contract-change` si es de contrato) y **no** lo
resuelvas en el código.

## 10. Cómo reportar al terminar

En la descripción del PR:

```
Tarea: T-xx — <título>
Cierre verificado: <comando> → <resultado resumido>
Principios tocados: I, III, …    Observaciones cerradas: O-05, O-27 …
Hallazgos fuera de alcance: <lista o "ninguno">
```
