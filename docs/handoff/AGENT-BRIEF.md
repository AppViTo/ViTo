# AGENT BRIEF — Contexto completo para agentes implementadores

> **Para quién:** cualquier agente (frontend, backend o DevOps) que vaya a implementar
> tareas de [`docs/roadmap/tasks.md`](../roadmap/tasks.md).
> **Léelo completo antes de tomar tu primera tarea.** Vigente desde 2026-10-16; §6 al día el 2026-11-10.

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

## 6. Estado real del repo (2026-11-10) — lo que vas a encontrar

El estado de cada tarea está en `docs/roadmap/tasks.md`; esta sección resume lo que más
confunde a un agente nuevo. Si algo de aquí contradice a `tasks.md` o a `develop`, gana el repo.

| Tema | Realidad |
|------|----------|
| Olas del contrato (D-08) | **1, 2a y 3 aprobadas**; 2b (check-in/QR), 4, 5 y 6 pendientes. Specs: `specs/003` (Ola 1), `specs/004` (Ola 2a). La Ola 2a no tiene contrato aún (T-C11) |
| Ramas `contract/ola-N` | Su contenido entró a `develop` por otros PR. **No mergear** `contract/ola-1`: es anterior a `develop` y revertiría `scope=invited` y el límite por IP de la vista previa |
| `asyncapi.yaml` y fixtures | Existen (`docs/api/fixtures/`, `npm run mock:api`), pero `asyncapi.yaml` sigue en v0 como RFC (T-C05). Por eso siguen bloqueados el chat en WS crudo (T-B06), el outbox de Kafka (T-B36) y `/ws/checkin` |
| Gateway | Rutea `/api/v1/**` y `/ws/{checkin,mosaic,streaming,chat}/**` y responde con el envelope de ADR-003. Las rutas `/internal/**` (INTERNO en `endpoint-inventory.md`) no pasan por él |
| Errores | Los 9 servicios Java heredan de `BaseExceptionHandler` en `backend/shared-web` (T-B18, ADR-017) |
| Backend | Java 21, Spring Boot 4.0.9 (T-B31). `backend/` es un reactor Maven: `cd backend && ./mvnw -pl <svc> -am test`. Ya no hay `mvnw` por servicio |
| Kafka | No hay outbox ni sobre versionado (T-B36): ningún consumidor nuevo hasta cerrarlo |
| Mongo | Un usuario por servicio (T-I19, #302) y una base por servicio: `chat` y `media` salen de `vito_db` a `vito_chat` y `vito_media` (T-B40). Hasta el próximo despliegue, producción sigue con root y `vito_db` compartida |
| Despliegue | ADR-011: VPS con Docker Compose (`docker-compose.prod.yml`), CD real con aprobación y rollback (T-I08) y web en Cloudflare Pages (T-I11). No trabajar en `infrastructure/kubernetes/` |
| App de la Ola 1 | El backend está en producción desde v0.2.0. En la app faltan las tareas `[Alex]` abiertas de `specs/003` (editar, cancelar, enlaces, asistentes, `getCurrentUser`) y el cierre T065 |

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

**Antes de elegir tarea** (T-I15): revisa los issues abiertos que bloquean a la otra pista
por algo de la tuya — `blocked-by-backend` si eres la pista backend/infra (T-B, T-I),
`blocked-by-frontend` si eres la pista contrato/frontend (T-C, T-F):

```bash
gh issue list --label blocked-by-backend --state open    # o blocked-by-frontend
```

Atenderlos tiene prioridad sobre las tareas P1/P2 de tu pista (no sobre una P0). Si la
tarea que piden no está 🟢 READY o choca con otra P0, coméntalo en el issue y déjalo para
el sync del miércoles; no cambies tú su prioridad.

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
cd backend && ./mvnw -pl <svc> -am clean verify   # reactor con shared-web (T-B18)

# Stack local completo
docker compose --profile app up -d --build

# Frontend
cd frontend/vito_app && flutter pub get && flutter analyze --fatal-infos && flutter test

# Contrato
npm ci
npm run contract            # Spectral + fixtures contra su schema (= job "Contract" de CI)
npm run test:smoke          # tests del smoke E2E, sin red (= job "Contract" de CI)
npm run smoke:e2e           # smoke real; requiere E2E_BASE_URL/E2E_EMAIL/E2E_PASSWORD (T-I21)
npm run lint:async          # cuando exista asyncapi.yaml (T-C05)
npm run mock:api            # Prism en :4010

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

### 9.1 Bloqueos entre pistas (T-I15)

Si tu tarea depende de una tarea de la **otra pista** (backend/infra: T-B, T-I ·
contrato/frontend: T-C, T-F):

1. Abre un issue con la plantilla **Bloqueo entre pistas**
   (`.github/ISSUE_TEMPLATE/blocked.md`) y la etiqueta de la pista que te bloquea:
   `blocked-by-backend` o `blocked-by-frontend`. Título: `[blocked] T-xx ← T-yy: <qué falta>`.
2. En el issue: tu tarea bloqueada, la tarea que propones adelantar (T-yy), el impacto y si
   hay un rodeo temporal (p. ej. Prism con los fixtures del contrato).
3. Marca tu tarea `🔴 BLOQUEADA por T-yy` en `tasks.md` con el enlace al issue y sigue con
   otra tarea 🟢 READY de tu pista.
4. **No implementes la otra pista**, ni un parche de ella en tu código. Un rodeo temporal
   solo vale si no adelanta trabajo de la otra pista (un mock, un flag), y se retira al
   cerrar el issue.
5. La otra pista lo atiende según §7. Los leads repriorizan T-yy en `tasks.md` en el sync
   del miércoles; el agente no cambia la prioridad de tareas ajenas.
6. Al quedar T-yy ✅ HECHA, se cierra el issue y tu tarea vuelve a 🟢 READY.

## 10. Cómo reportar al terminar

En la descripción del PR:

```
Tarea: T-xx — <título>
Cierre verificado: <comando> → <resultado resumido>
Principios tocados: I, III, …    Observaciones cerradas: O-05, O-27 …
Hallazgos fuera de alcance: <lista o "ninguno">
```
