# Backlog — Alineación contrato ↔ implementación

> Backlog único. **Un agente solo toma tareas 🟢 `READY`.** Antes de tomar una, leer
> [`../handoff/AGENT-BRIEF.md`](../handoff/AGENT-BRIEF.md).
>
> Al tomar una tarea: cambiar su estado a `🔵 EN CURSO (<rama>)` en el mismo PR.
> Al cerrar: `✅ HECHA (#44)`. Si al implementar descubres que la tarea toca el contrato
> o una decisión pendiente, **detente**, márcala `⛔ BLOQUEADA (motivo)` y avisa.

## Estados

| Estado | Significado |
|--------|-------------|
| 🟢 `READY` | Se puede implementar y mergear ya. No cambia el contrato. |
| 🟡 `RFC` | Se puede **redactar** ya, pero toca `docs/api/**` o `docs/decisions/**`: requiere issue `contract-change` y aprobación de 1 CODEOWNER antes de mergear. |
| 🔴 `BLOQUEADA` | Espera una decisión (D-xx en `decisions-pending.md`) u otra tarea (T-xx). |
| 🔵 `EN CURSO` | Alguien la tomó (indicar rama). |
| ✅ `HECHA` | Mergeada con su criterio de cierre verificado. |
| ⚪ `REEMPLAZADA` / `DIFERIDA` | Ya no aplica en la fase actual (se indica qué la reemplaza o cuándo vuelve). |

## Bugs reportados en Jira

Un issue de VITO cuyo resumen empieza con `Bug-` (p. ej. `Bug-F01 · …`) es la única
excepción al sentido repo → Jira: nace en Jira y entra al backlog por triage.

1. **Triage:** leer el reporte y sus capturas, ubicar la causa en el código y decidir si
   procede (es reproducible o evidente en el código, y es un defecto, no una feature nueva).
2. **Si procede:** se registra en la sección [Bugs de producción](#bugs-de-producción) con su
   mismo ID (`Bug-F01`, `Bug-B01`…), prioridad **P0** y enlace al issue. Queda 🟢 `READY` si se
   arregla con lo que ya está en el contrato; si necesita un endpoint nuevo, la parte que falta
   va como 🟡 `RFC` y el resto se arregla igual.
3. **Si no procede:** se comenta en el issue el motivo (duplicado, no reproducible, es una
   feature) y no entra al backlog.

## Pistas y dueño sugerido

| Pista | Prefijo | Dueño sugerido | Rama |
|-------|---------|----------------|------|
| Contrato y tooling | `T-C` | Agente frontend (dueño de `docs/api/**`) | `contract/<nombre>` o `docs/<nombre>` |
| Frontend Flutter | `T-F` | Agente frontend | `frontend/<nombre>` |
| Backend Java/Python | `T-B` | Agente backend | `backend/<nombre>` |
| Infra, CI/CD, higiene | `T-I` | DevOps / cualquiera | `docs/<nombre>`, `backend/<nombre>` o `chore` |

### Asignación del 2026-10-28 (agente frontend)

Reparto para el agente que trabaja con Alex Villa (`@alexvilla01`, dueño de `frontend/**`,
`docs/api/**` y `docs/roadmap/**` en CODEOWNERS). Esta sección solo indica el dueño sugerido
de hoy; los estados no cambian aquí. El agente marca 🔵 `EN CURSO` en el PR que tome la tarea.

| ID | Tarea | Asignada a | Qué puede avanzar hoy |
|----|-------|------------|------------------------|
| T-C05 | `asyncapi.yaml` v0 (🟡 RFC) | Agente frontend | Redactar la propuesta con las 4 rutas WS y abrir el issue `contract-change`. No se mergea sin aprobación de 1 CODEOWNER. |
| T-I15 | Protocolo de bloqueos entre agentes | Agente frontend | AGENT-BRIEF §7 y §9, enlace desde CONTRIBUTING y `.github/ISSUE_TEMPLATE/blocked.md`. Un solo PR. |
| T-F09 | Prueba en dispositivo de `mobile_scanner` 7 y `share_plus` 10 | Alex Villa (manual) | Requiere teléfono físico; el agente no puede ejecutarla. Resultado por plataforma en el issue o PR. |

**Sin asignar al agente frontend:**
- **T-I23** (recomendaciones sin servicio en prod): `frontend/vito_app/lib` no consume
  `GET /recommendations/events`, así que la parte de la app no existe todavía. El fallback
  es del gateway (backend/infra).
- **T-C07 Ola 3:** figura en curso en `contract/ola-3`. Confirmar con Alex antes de tocar nada
  del contrato.

---

## Resumen: qué se puede hacer desde ya

| ID | Tarea | Pista | Prioridad | Estado | Jira |
|----|-------|-------|-----------|--------|------|
| T-C01 | Fixtures de las 15 operaciones existentes | C | P0 | ✅ HECHA (#21) | [VITO-10](https://bernardo-mira.atlassian.net/browse/VITO-10) |
| T-C02 | `package.json` + mock REST con Prism | C | P0 | ✅ HECHA (#27) | [VITO-11](https://bernardo-mira.atlassian.net/browse/VITO-11) |
| T-C03 | Job de CI "contract": Spectral + validación de fixtures | C | P0 | ✅ HECHA (#44) | [VITO-12](https://bernardo-mira.atlassian.net/browse/VITO-12) |
| T-C04 | Tests Dart de serialización contra fixtures | C/F | P0 | ✅ HECHA (#31) | [VITO-13](https://bernardo-mira.atlassian.net/browse/VITO-13) |
| T-C05 | `asyncapi.yaml` v0 con las 4 rutas WS de CONVENTIONS §7 | C | P0 | 🟡 RFC | [VITO-14](https://bernardo-mira.atlassian.net/browse/VITO-14) |
| T-C06 | `listEvents` con `scope=mine\|upcoming` según ADR-014 | C | P0 | ✅ HECHA (#147) | [VITO-15](https://bernardo-mira.atlassian.net/browse/VITO-15) |
| T-C08 | Alinear `AuthResponse`, `User` de auth y `logoutUser` de openapi/fixtures/tests Dart con ADR-004 | C/F | P0 | ✅ HECHA (#45) | [VITO-17](https://bernardo-mira.atlassian.net/browse/VITO-17) |
| T-F01 | AuthBloc real: login/register/logout/refresh sin mocks | F | P0 | ✅ HECHA (#46, #56) | [VITO-18](https://bernardo-mira.atlassian.net/browse/VITO-18) |
| T-F03 | Proveer `CheckInBloc` en raíz y ruta de `ArchiveDetailPage` | F | P1 | ✅ HECHA (#63) | [VITO-20](https://bernardo-mira.atlassian.net/browse/VITO-20) |
| T-F04 | WS clients envían `?token=` (checkin, mosaic, streaming, chat sin `?userId=`) | F | P0 | ✅ HECHA (#60) | [VITO-21](https://bernardo-mira.atlassian.net/browse/VITO-21) |
| T-F06 | Build web falla si faltan `API_BASE_URL`/`WS_BASE_URL` | F | P1 | ✅ HECHA (#64) | [VITO-23](https://bernardo-mira.atlassian.net/browse/VITO-23) |
| T-F07 | Rellenar tests vacíos y subir cobertura de auth | F | P1 | ✅ HECHA (#65) | [VITO-24](https://bernardo-mira.atlassian.net/browse/VITO-24) |
| T-F09 | Probar en dispositivo `mobile_scanner` 7 y `share_plus` 10 (#158) antes del evento | F | P0 | 🟢 READY (manual) | — |
| T-F10 | Moderación del mosaico en la app del organizador (excepción A3) | F | P0 | ✅ HECHA (#185) | — |
| T-B01 | Gateway: borrar `X-User-*` entrantes en todas las rutas + rate limit por IP en públicas | B | P0 | ✅ HECHA (#19) | [VITO-26](https://bernardo-mira.atlassian.net/browse/VITO-26) |
| T-B02 | Gateway: 401/fallbacks con envelope ADR-003 | B | P1 | ✅ HECHA (#38) | [VITO-27](https://bernardo-mira.atlassian.net/browse/VITO-27) |
| T-B04 | Gateway: ruta `/ws/**` con validación de `?token=` y cierre 4401 | B | P0 | ✅ HECHA (#22, re-entrega de #20) | [VITO-29](https://bernardo-mira.atlassian.net/browse/VITO-29) |
| T-B05 | `WebSocketConfig` WS crudo en mosaic y streaming | B | P0 | ✅ HECHA (#25) | [VITO-30](https://bernardo-mira.atlassian.net/browse/VITO-30) |
| T-B08 | `hideAddressUntilAccepted` en event-service | B | P1 | ✅ HECHA (#26) | [VITO-33](https://bernardo-mira.atlassian.net/browse/VITO-33) |
| T-B09 | Agregar servicios FastAPI a `docker-compose.yml` | B/I | P1 | ✅ HECHA (#43) | [VITO-34](https://bernardo-mira.atlassian.net/browse/VITO-34) |
| T-B12 | event-service: `settings.isPublic` se serializa como `"public"` y se ignora al recibir | B | P0 | ✅ HECHA (#28) | [VITO-37](https://bernardo-mira.atlassian.net/browse/VITO-37) |
| T-B13 | `boolean is*` serializado sin `is` en archive, mosaic, social y streaming | B | P1 | 🔴 BLOQUEADA (olas 2–5) | [VITO-38](https://bernardo-mira.atlassian.net/browse/VITO-38) |
| T-B11 | Gateway: rate limiter y circuit breaker inactivos en perfiles `dev`/`docker` | B | P0 | ✅ HECHA (#24) | [VITO-36](https://bernardo-mira.atlassian.net/browse/VITO-36) |
| T-I01 | Higiene: `.gitignore` `backend/out/`, borrar `backend/java/` huérfano | I | P2 | 🟢 READY | [VITO-42](https://bernardo-mira.atlassian.net/browse/VITO-42) |
| T-I02 | `integration-tests/`: darle `pom.xml` o retirarlo | I | P2 | 🟢 READY | [VITO-43](https://bernardo-mira.atlassian.net/browse/VITO-43) |
| T-I03 | `docs/architecture.md` alineado a ADR-001 y al inventario real | I | P2 | 🟢 READY | [VITO-44](https://bernardo-mira.atlassian.net/browse/VITO-44) |
| T-B14 | Actuator + health + métricas Prometheus en todos los servicios | B | P0 | ✅ HECHA (#33) | [VITO-39](https://bernardo-mira.atlassian.net/browse/VITO-39) |
| T-B15 | Códigos y errores de auth-service y `createEvent` según los fixtures | B | P0 | ✅ HECHA (#39) | [VITO-40](https://bernardo-mira.atlassian.net/browse/VITO-40) |
| T-B16 | `expiresIn` en segundos y forma de `AuthResponse` según ADR-004 | B | P0 | ✅ HECHA (#40) | [VITO-41](https://bernardo-mira.atlassian.net/browse/VITO-41) |
| T-B17 | `/events/upcoming` lista eventos privados ajenos (filtro `settings.isPublic`) | B | P0 | ✅ HECHA (#153) | [VITO-56](https://bernardo-mira.atlassian.net/browse/VITO-56) |
| T-B03 | Envelope ADR-003 en los 6 servicios sin handler (copia temporal, ADR-017) | B | P1 | ✅ HECHA (#164) | [VITO-28](https://bernardo-mira.atlassian.net/browse/VITO-28) |
| T-B18 | Parent POM + `backend/shared-web`; borrar copias de filtro JWT y handler (ADR-017) | B/I | P1 | 🔴 BLOQUEADA (Ola 1) | — |
| T-B19 | media-service: magic bytes, MIME de CONVENTIONS §8 y límite de 10 MB | B | P0 | ✅ HECHA (#155) | — |
| T-B20 | chat-service: orígenes del WS desde configuración (hoy fijos en `localhost`) | B | P0 | ✅ HECHA (#163) | — |
| T-B21 | Revocación inmediata de access tokens (lista negra por `jti` en el gateway) | B | P1 | ✅ HECHA (#168) | — |
| T-B22 | Throttling de login por cuenta | B | P1 | 🟡 RFC | — |
| T-B23 | Rate limit por usuario en las rutas autenticadas | B | P1 | ✅ HECHA (#180) | — |
| T-B24 | Audit log de eventos de seguridad | B | P1 | 🟢 READY | — |
| T-B25 | media-service: re-codificar imágenes y quitar metadatos (excepción A3) | B | P0 | ✅ HECHA (#174) | — |
| T-B26 | Moderación del mosaico por el organizador (excepción A3) | B | P0 | ✅ HECHA (#189) | — |
| T-B27 | Logs en INFO en `prod` en los 9 servicios Java restantes | B | P1 | ✅ HECHA (#175) | — |
| T-B28 | auth, chat y media: 404/405 sin 500 y 401/403 de Spring Security con envelope | B | P1 | 🟢 READY | — |
| T-B29 | `createEvent` con `@Valid` (400 del fixture) | B | P1 | 🟢 READY | — |
| T-B30 | JWT con firma asimétrica (RS256/ES256 + JWKS) | B | P1 | 🔴 BLOQUEADA (requiere spec) | — |
| T-B31 | Spring Boot con soporte en los 10 servicios Java (excepción A9, vence el 2026-11-07) | B | P0 | 🔵 EN CURSO (backend/t-b31-spring-boot-soporte, spec) | — |
| T-B32 | Reacciones y mensajes del streaming no llegan en vivo a los espectadores (fan-out sin Redis Pub/Sub ni consumidor Kafka) | B | P1 | 🟡 RFC (contrato de reacciones; T-C05) | — |
| T-I07 | `docker-compose.prod.yml` + Caddy + límites de memoria (ADR-011) | I | P0 | ✅ HECHA (#36) | [VITO-46](https://bernardo-mira.atlassian.net/browse/VITO-46) |
| T-I09 | Backup diario de Mongo a almacenamiento S3 (R2) + restauración | I | P0 | ✅ HECHA (#35) | [VITO-48](https://bernardo-mira.atlassian.net/browse/VITO-48) |
| T-I10 | media-service con endpoint S3 configurable (R2 en prod, SeaweedFS en local) | B | P0 | ✅ HECHA (#34) | [VITO-49](https://bernardo-mira.atlassian.net/browse/VITO-49) |
| T-I12 | CI solo construye y prueba lo que cambió (filtros `paths`) | I | P1 | 🟢 READY | [VITO-51](https://bernardo-mira.atlassian.net/browse/VITO-51) |
| T-I06 | Aprovisionar cuentas y VPS (tarea manual de leads) | I | P0 | ✅ HECHA (manual, 2026-10-24; pendientes menores en ADR-013) | [VITO-45](https://bernardo-mira.atlassian.net/browse/VITO-45) |
| T-I08 | Pipeline de despliegue a producción por SSH | I | P0 | 🔵 EN CURSO (despliegue verificado; falta ensayar el rollback) | [VITO-47](https://bernardo-mira.atlassian.net/browse/VITO-47) |
| T-I11 | Frontend web en Cloudflare Pages, con la CSP/HSTS de la web (antes en T-I18) | I/F | P1 | 🔵 EN CURSO (#179 · falta verificar en prod) | [VITO-50](https://bernardo-mira.atlassian.net/browse/VITO-50) |
| T-I13 | Grafana Cloud: métricas, logs y uptime | I | P1 | 🟢 READY | [VITO-52](https://bernardo-mira.atlassian.net/browse/VITO-52) |
| T-I14 | Runbook de restauración y de incidentes | I | P1 | 🔵 EN CURSO (#167 · falta el ensayo manual) | [VITO-53](https://bernardo-mira.atlassian.net/browse/VITO-53) |
| T-I15 | Protocolo de bloqueos entre agentes (frontend ↔ backend/infra) | I | P1 | 🟢 READY | [VITO-54](https://bernardo-mira.atlassian.net/browse/VITO-54) |
| T-I16 | Protección de rama en `main` y `develop` (tarea manual de leads) | I | P0 | ✅ HECHA (#49, manual) | [VITO-55](https://bernardo-mira.atlassian.net/browse/VITO-55) |
| T-I17 | Gateway en `prod`: rutas de subida cerradas hasta la ola del mosaico (excepción A3) | I/B | P0 | ✅ HECHA (#156) | — |
| T-I18 | Cabeceras de seguridad en Caddy (`api.`, `live.`); la CSP/HSTS de la web pasó a T-I11 | I | P0 | ✅ HECHA (#157) | — |
| T-I19 | Un usuario de Mongo por servicio (sin root) | I/B | P1 | 🟢 READY | — |
| T-I20 | Kafka con TLS y ACL por tópico (excepción A7) | I | P2 | ⚪ DIFERIDA (Fase Crecimiento; revisión 2027-04-22) | — |
| T-I21 | Subida al mosaico en `prod` detrás de una propiedad (excepción A3) | I/B | P0 | ✅ HECHA (#177, #183 · activar tras T-B26) | — |
| T-I22 | `cd.yml`: opción para reconstruir todas las imágenes (VPS vacío) | I | P1 | 🟢 READY | — |
| T-I23 | Recomendaciones en `prod` sin `recommendation-service`: fallback y degradación | I/F | P2 | 🟢 READY | — |
| T-I24 | Registro de imágenes para no depender del VPS | I | P2 | ⚪ DIFERIDA (Fase Crecimiento) | — |

**Bloquean el primer release** (triage de seguridad, [T051](../../backend/SECURITY_CHECKLIST.md)): T-B17, T-B19, T-I17, T-I18 y
T-I11 para la web (lleva la CSP que antes era de T-I18), más T048–T050 de [`specs/001-cd-produccion`](../../specs/001-cd-produccion/tasks.md) (Trivy,
gitleaks, Dependabot). Las de P1 del triage bloquean la apertura a usuarios reales.

Bloqueadas (no tomar): T-C07 olas 1–2 y 4–6 (VITO-16), T-F02 (VITO-19), T-B06 (VITO-31), T-B10 (VITO-35), T-B13 (VITO-38), T-B18, T-B30.
Desbloqueadas el 2026-10-24: T-C07 Ola 3 (D-08), T-B07 y T-F08 (T-C06 hecha en #147), y T-I11
(Pages, dominio, secretos y variables listos en T-I06).
Reemplazadas por ADR-011: T-I04 (→ T-I08) y T-I05 (Kubernetes → Fase Crecimiento).

---

## Bugs de producción

Triage del 2026-10-26 (release 0.1.0). Causa común: `home_screen.dart` y `people_screen.dart`
leen de `MockApiService` en vez del backend. Los eventos ya tienen contrato
(`GET /events?scope=mine|upcoming`, T-C06/T-F08); amigos, historias y transmisiones en vivo
todavía no (Olas 1–2), así que se muestran vacíos. Esto adelanta la parte de T-F02 que no
depende del contrato.

| ID | Bug | Prioridad | Estado | Jira |
|----|-----|-----------|--------|------|
| Bug-F01 | Eventos mock en producción que dan 404 y Retry no permite volver | P0 | ✅ HECHA (#198, hotfix #199) | [VITO-76](https://bernardo-mira.atlassian.net/browse/VITO-76) |
| Bug-F02 | El nombre del dashboard es fijo, no el del usuario con sesión | P0 | ✅ HECHA (#198, hotfix #199) | [VITO-77](https://bernardo-mira.atlassian.net/browse/VITO-77) |
| Bug-F03 | El evento creado no aparece en próximos eventos ni hay lista de "mis eventos" | P0 | 🟢 READY (el home ya carga `scope=mine`; falta la sección propia) | [VITO-78](https://bernardo-mira.atlassian.net/browse/VITO-78) |
| Bug-F04 | Quitar los datos mock del dashboard y mostrar estados vacíos | P0 | ✅ HECHA (#198, hotfix #199) | [VITO-79](https://bernardo-mira.atlassian.net/browse/VITO-79) |
| Bug-F05 | El buscador solo encuentra eventos mock | P0 | ✅ HECHA (#198, hotfix #199) | [VITO-80](https://bernardo-mira.atlassian.net/browse/VITO-80) |
| Bug-F06 | "Mi Gente" muestra amigos mock a cualquier usuario (privacidad) | P0 | ✅ HECHA (#198, hotfix #199) | [VITO-81](https://bernardo-mira.atlassian.net/browse/VITO-81) |

- **Bug-F04 y Bug-F06:** retirar `MockApiService`. Sin datos reales, mostrar "Aún no tienes
  eventos para asistir", "Aún no tienes amigos", "Aún no hay historias por mostrar" y "No hay
  transmisiones en vivo de tus eventos".
- **Bug-F01:** desaparece con Bug-F04. Además, Retry en el detalle de un evento 404 debe
  permitir volver atrás.
- **Bug-F02:** el saludo toma el nombre del `AuthBloc` (como ya hace `people_screen.dart`).
- **Bug-F03 y Bug-F05:** el home carga `scope=upcoming` y `scope=mine`, y el buscador filtra
  sobre esos datos. Si "mis eventos" necesita una sección propia en el home, se diseña ahí.

### Triage del 2026-10-27: Bug-F07 y la hora de los eventos

| ID | Bug | Prioridad | Estado | Jira |
|----|-----|-----------|--------|------|
| Bug-B01 | Las fechas de los eventos se muestran en UTC: un evento de las 8 pm en Bogotá aparece a la 1 am del día siguiente | P0 | 🟢 READY | [VITO-85](https://bernardo-mira.atlassian.net/browse/VITO-85) (reportado junto con VITO-82) |
| Bug-F07 | El organizador no puede editar su evento (fecha, foto, descripción, lugar) | P0 | 🟡 RFC (`updateEvent` entra al contrato en la Ola 1) | [VITO-82](https://bernardo-mira.atlassian.net/browse/VITO-82) |
| Bug-B02 | Los eventos se crean en `DRAFT` y no hay forma de publicarlos: nunca salen en "Próximos eventos" | P0 | 🟢 READY | [VITO-86](https://bernardo-mira.atlassian.net/browse/VITO-86) |
| Bug-F08 | Al crear un evento, la búsqueda de usuarios para invitar nunca encuentra a nadie | P0 | 🟢 READY (avisar en la pantalla); la búsqueda real, 🟡 RFC [AppViTo/ViTo#204](https://github.com/AppViTo/ViTo/issues/204) | [VITO-83](https://bernardo-mira.atlassian.net/browse/VITO-83) |
| Bug-F10 | El detalle del evento está en inglés ("About", "Date & Time", "Statistics", "Draft", "Private") | P0 | 🟢 READY | [VITO-87](https://bernardo-mira.atlassian.net/browse/VITO-87) |
| Bug-F09 | "Copiar enlace de invitación" y "Enviar por WhatsApp" prometen un enlace que nunca se genera | P0 | 🟢 READY (ocultarlos); el enlace real, 🟡 RFC [AppViTo/ViTo#204](https://github.com/AppViTo/ViTo/issues/204) | [VITO-88](https://bernardo-mira.atlassian.net/browse/VITO-88) |

- **Bug-B01, causa:** la app envía bien la hora. `create_event_screen.dart` toma las 20:00
  locales y `EventDetailsModel.toJson` las manda en UTC (`2026-12-10T01:00:00.000Z`), como pide
  el contrato. Pero `event-service` guarda `details.startDate`/`endDate` como `LocalDateTime`:
  Jackson descarta la `Z` y responde `2026-12-10T01:00:00`, sin zona. La app lo lee con
  `DateTime.parse`, que interpreta un texto sin zona como hora local, y muestra la 1:00 am. La
  diferencia de 5 h es la de Bogotá (UTC−5) con UTC. Coincide con Londres solo porque en
  diciembre Londres está en UTC.
- **Bug-B01, arreglo (sin RFC, el contrato ya dice "ISO-8601 UTC con sufijo Z"):**
  - Backend: `startDate`, `endDate`, `createdAt` y `updatedAt` del evento como `Instant`, y
    `Instant.now()` en el filtro de `/events?scope=upcoming` (`EventMongoAdapter`). Los
    contenedores corren en UTC, así que los documentos guardados ya representan el instante
    correcto y no hace falta migrar datos.
  - Frontend: `EventDetailsModel.fromJson` y `EventModel.fromJson` convierten con `.toLocal()`
    para mostrar la hora del dispositivo.
  - Prueba: crear a las 20:00 en Bogotá, leer `getEvent` y ver `01:00:00Z` en la respuesta y
    8:00 pm en la app.
  - Los eventos ya creados se corrigen solos con el arreglo, porque el dato guardado era correcto.
- **Bug-F07:** procede como defecto (un organizador no puede corregir su propio evento), pero
  `PUT /events/{eventId}` no está en el contrato: el inventario lo marca para publicar en la
  Ola 1. `event-service` ya tiene el `@PutMapping` y la app tiene `UpdateEvent` en el bloc, sin
  pantalla. Requiere el RFC de `updateEvent` (o adelantar esa operación de la Ola 1) antes de
  implementar la pantalla de edición.
- **Notificar a los invitados de los cambios** (pedido en VITO-82) **no procede como bug:** es
  una feature nueva sin contrato de notificaciones. Va al backlog de features, no a esta tabla.
- **Bug-B02:** `EventServiceImpl.createEvent` fija `status(DRAFT)`, pero el contrato responde
  `PUBLISHED` (fixture `createEvent.201.json`) y no existe ninguna operación para publicar
  (`UpdateEventCommand` ni siquiera tiene `status`). `scope=upcoming` solo lista `PUBLISHED` y
  públicos, así que ningún evento creado desde la app aparece ahí; solo se ven en
  `scope=mine`. Gana el contrato (ADR-010): crear en `PUBLISHED`. Los eventos ya creados en
  `DRAFT` necesitan una actualización de una vez en Mongo (`DRAFT` → `PUBLISHED`), que se corre
  con el despliegue. Si los leads quieren borradores de verdad, eso es una feature nueva con RFC.
- **Bug-F08 (VITO-83):** `create_event_screen.dart` busca invitados con `GET /social/search`, pero
  `SocialServiceImpl.searchUsers` es un stub que siempre devuelve una lista vacía ("For now, we'll
  return an empty list"). Además, la operación no está en el contrato: el inventario la marca
  `PUBLICAR` en la Ola 2, sin aprobar por D-08. Procede como defecto, porque la pantalla ofrece
  algo que no funciona.
  - Arreglo inmediato (READY): cuando la búsqueda vuelva vacía, decir que la búsqueda de usuarios
    aún no está disponible en lugar de "no encontramos a nadie", o esconder la caja.
  - Arreglo real: `searchUsers` se sumó al RFC #204 como adelanto de la Ola 2. Implementarlo
    no basta: `social-service` busca en su colección `user_profiles` y nadie la llena
    (`auth-service` publica el registro en `user-events`, pero ningún servicio lo consume). El
    RFC pide consumir ese tópico y hacer una carga inicial de los usuarios que ya existen.
- **Bug-F10** (antes se registró como Bug-F08; se renombró porque VITO-83 ya usaba ese ID):
  la app está en español (`app_localization_test.dart`), pero
  `event_detail_page.dart` tiene los textos en inglés. Pasarlos al español, incluidos los estados
  (`Draft` → "Borrador", `Private` → "Privado") y la fecha, que sale como "Saturday, December 26"
  porque el `DateFormat` no recibe la locale `es`.
- **Bug-F09:** `create_event_screen.dart` muestra "Copiar enlace de invitación" ("Se crea con el
  evento y caduca en 7 días") y "Enviar por WhatsApp", pero `_inviteLink` nunca se asigna: los
  dos botones solo dicen "El enlace se genera al crear el evento". Arreglo inmediato: ocultarlos
  hasta que exista el enlace.
- **Invitar después de crear el evento y compartir su enlace:** no es un bug, es la Ola 1 del
  inventario. `POST /events/{eventId}/invitations` ("app + link externo") y
  `GET /events/{eventId}/invitations` están marcados `PUBLICAR`, pero la Ola 1 no está aprobada
  (D-08 solo aprobó la Ola 3). El backend tiene los endpoints, aunque `Invitation` solo invita a
  usuarios registrados (`inviteeId`): no hay token ni enlace externo. Para adelantarlo: aprobar la
  Ola 1 por D-08 (o solo estas operaciones), RFC `contract-change` con las rutas, el modelo del
  enlace (token, caducidad de 7 días, quién puede usarlo) y sus fixtures, y después backend y
  pantalla.

### Triage del 2026-10-28: recuperar contraseña

| ID | Bug | Prioridad | Estado | Jira |
|----|-----|-----------|--------|------|
| Bug-F11 | "¿Olvidaste tu contraseña?" no hace nada: quien olvida su contraseña pierde la cuenta | P0 | 🟢 READY (decir qué hacer); el flujo real, 🟡 RFC [AppViTo/ViTo#213](https://github.com/AppViTo/ViTo/issues/213) | [VITO-89](https://bernardo-mira.atlassian.net/browse/VITO-89) |

- **Causa:** en `login_page.dart`, `_buildForgotPassword` tiene `onPressed: () {}`. Detrás no hay
  nada en ninguna capa: el contrato no tiene operaciones de recuperación, el inventario no las
  lista en ninguna ola, `auth-service` no tiene endpoints ni tokens de restablecimiento, y no hay
  correo transaccional (Resend está elegido en ADR-011, pero su configuración quedó como
  pendiente menor de T-I06 y figura "Por configurar" en ADR-013).
- **Arreglo inmediato (READY, solo frontend):** que el botón abra un aviso con qué hacer mientras
  tanto (escribir al correo de soporte del equipo, sin ponerlo en el repo) en lugar de no
  responder. Sin contrato no se puede más.
- **Arreglo real (RFC [AppViTo/ViTo#213](https://github.com/AppViTo/ViTo/issues/213), fuera de las olas actuales):**
  - `POST /api/v1/auth/password/forgot` `{email}`: siempre `202`, exista o no la cuenta, para no
    revelar qué correos están registrados. Rate limit por IP y por correo.
  - `POST /api/v1/auth/password/reset` `{token, newPassword}`: `204`; token de un solo uso,
    aleatorio, guardado como hash, que caduca en 30–60 min; revoca los refresh tokens del usuario.
  - Las dos rutas, públicas en el gateway (`PUBLIC_PATHS`).
  - Correo con Resend (terminar T-I06), con el enlace `https://<app>/reset-password?token=…`.
  - App: pantallas "Olvidé mi contraseña" y "Nueva contraseña", y la ruta web del enlace.

---

## Pista C — Contrato y tooling

### T-C01 · Fixtures de las 15 operaciones existentes — ✅ HECHA (#21) · P0 · O-01
- **Qué:** crear `docs/api/fixtures/<tag>/<operationId>.<status>.json` (request y
  response) para cada operación de `openapi.yaml`, incluidos los errores 400/401/404/409
  declarados. Añadir `docs/api/fixtures/README.md` con la convención de nombres.
- **Fuente:** los `examples` de `openapi.yaml` + `CONVENTIONS.md` §3–§6 + ADR-001/002/003.
- **No hacer:** cambiar schemas del YAML. Si un ejemplo contradice al schema, se corrige el
  ejemplo; si el schema parece mal, abrir RFC.
- **Cierre:** cada `operationId` tiene al menos un fixture 2xx y uno de error; T-C03
  los valida contra el schema.

### T-C02 · `package.json` + mock REST (Prism) — ✅ HECHA (#27) · P0 · O-04
- **Qué:** `package.json` en la raíz con `@stoplight/prism-cli`, `@stoplight/spectral-cli`,
  `@asyncapi/cli` fijados y scripts `lint:api`, `lint:async`, `mock:api` (Prism en :4010).
  `mock:ws` queda como script que falla con mensaje "pendiente de T-C05".
- **Cierre:** `npm ci && npm run mock:api` sirve las 15 operaciones; Flutter corre contra
  `http://localhost:4010/api/v1` (comando ya documentado en CONTRIBUTING §6).

### T-C03 · Job de CI "contract" — ✅ HECHA (#44) · P0 · Principio I
- **Qué:** job en `.github/workflows/ci.yml` que corre `npm run lint:api` y un script que
  valida cada fixture contra su schema (p. ej. `ajv` sobre los schemas de OpenAPI).
  `lint:async` se activa cuando exista `asyncapi.yaml`.
- **Cierre:** el job es requerido en los PR que tocan `docs/api/**` y está verde.

### T-C04 · Tests Dart contra fixtures — ✅ HECHA (#31) · P0 · Principio IV
- **Qué:** en `frontend/vito_app/test/contract/`, un test por fixture 2xx: carga el JSON
  de `docs/api/fixtures/`, hace `Model.fromJson` y `toJson` y compara campos.
  Modelos: `AuthResponse`/`User`, `EventModel`, chat (`ChatRoom`, `ChatMessage`), `Media`.
- **Cierre:** `flutter test test/contract` verde; un cambio de fixture rompe el test.

### T-C05 · `asyncapi.yaml` v0 — 🟡 RFC · P0 · O-01, ADR-006
- **Qué:** documentar las rutas WS que **ya** fija `CONVENTIONS.md` §7 (checkin, mosaic,
  streaming, chat) con el formato de frame `{type, data}`. Los `type` se toman de los
  handlers existentes (`*WebSocketHandler.java`) y de los repositorios Flutter que los
  consumen; donde difieran, listar la diferencia en el RFC. Mensajes Kafka se agregan por
  ola (no en v0). Fixtures de frames en `docs/api/fixtures/ws/`.
- **Cierre:** `npx @asyncapi/cli validate docs/api/asyncapi.yaml` verde; PR aprobado por
  1 CODEOWNER; `mock:ws` de T-C02 sirve los frames.

### T-C08 · Contrato de auth alineado con ADR-004 — ✅ HECHA (#45) · P0 · ADR-004, Principio I
- **Hallazgo (T-B15/T-B16):** ADR-004 (aceptada) fija la respuesta de `register`/`login`/
  `refresh` con `user` **plano** (`id, username, email, displayName, avatar`), **sin**
  `tokenType`, y `logout` → `204`; sus consecuencias pedían corregir `openapi.yaml`, pero no
  se hizo. T-C01 (fixtures) y T-C04 (tests Dart) siguieron al openapi sin corregir: `user`
  con `profile`/`stats`, `tokenType` y `logoutUser` `200`. El backend ya devuelve la forma
  de ADR-004, así que Flutter pierde `displayName`/`avatar` (los busca en `profile`).
  **Decisión de leads (2026-10-17): se mantiene ADR-004.**
- **Qué:** issue `contract-change` y PR `contract/auth-adr-004`:
  - `openapi.yaml`: `AuthResponse` sin `tokenType` y con `user` = nuevo schema `AuthUser`
    plano (el schema `User` completo queda para social); `logoutUser` → `204`.
  - Fixtures `authentication/*` 2xx con esa forma; `logoutUser.200.json` → `logoutUser.204`.
  - Flutter: modelo de usuario de auth que lea la forma plana; tests de T-C04 actualizados.
- **Desbloquea:** que T-F01 muestre nombre y avatar tras el login contra el backend real.
- **Cierre:** Spectral verde; tests Dart de contrato verdes con los fixtures nuevos; PR
  aprobado por 1 CODEOWNER.

### T-C06 · `listEvents` con `scope` (O-26) — ✅ HECHA (#147) · P0 · ADR-014
- **Qué:** issue `contract-change` y PR `contract/events-listing`:
  - `openapi.yaml`: parámetro `scope` (enum `mine|upcoming`, default `mine`) en
    `listEvents`; descripción de contenido y orden de cada scope (en `upcoming`, orden por
    `details.startDate` ascendente, excepción a CONVENTIONS §5); `400` con
    `ErrorResponse` para un `scope` desconocido o `size` > 100.
  - Fixtures `listEvents` para `mine`, `upcoming` y el `400`.
- **No hacer:** publicar `/events/my-events` ni `/events/upcoming`; agregar parámetros de
  identidad (`organizerId`, `userId`).
- **Desbloquea:** T-B07, T-F08.
- **Cierre:** Spectral y job Contract verdes; PR aprobado por 1 CODEOWNER.
- **Complemento (#147):** para poder tener dos respuestas 200 del mismo endpoint, los
  fixtures con variantes ahora usan `<operationId>.<ejemplo>.<status>.json` cuando el media
  type declara `examples` (validador y README actualizados). `listEvents.200.json` pasa a
  `listEvents.mine.200.json`.

### T-C07 · Ramas `contract/ola-N` (1 a 6) — 🔵 EN CURSO (contract/ola-3) · D-08 aprobada 2026-10-24 (por ola) · O-02, O-30, ADR-016
- **Ola 3 REST (#182):** las 7 rutas del handoff más `removeMosaicItem`
  (`DELETE /mosaic/item/{itemId}`, moderación del organizador), `ITEM_REMOVED` en
  `asyncapi.yaml`, 33 fixtures en `fixtures/mosaic/` y CONVENTIONS §8 sin `video/mp4`. La
  escribió la sesión de terminal; el lead la aprueba. Quedan de la ola `DELETE /media/{mediaId}`
  y `GET /media/{mediaId}/presigned-url`, que el evento de prueba no necesita.
- **Ola 3 (aprobada por D-08 el 2026-10-24, #169):** #171 incorporó `/ws/mosaic/{eventId}` en
  `asyncapi.yaml` y el handoff `docs/handoff/ola-3-mosaico.md`. Falta el endpoint de moderación
  del organizador que pide la excepción A3 (lo implementa T-B26).
  Prioridad P0: el evento de prueba necesita el mosaico. Desbloquea T-F05, T-B26 y T-F10.
- **Definiciones de la excepción A3 para la Ola 3 (leads, 2026-10-24):**
  - **Post-moderación:** la foto aparece en el mosaico al instante y el organizador la oculta
    o la retira después. No hay cola de aprobación previa.
  - **Solo imágenes:** sacar `video/mp4` de CONVENTIONS §8 mientras rija la excepción (quedan
    `image/jpeg`, `image/png` e `image/webp`).
- **Qué:** por cada ola aprobada en `endpoint-inventory.md`: paths + schemas en
  `openapi.yaml`, frames/eventos en `asyncapi.yaml`, fixtures, ADR si aplica y handoff
  `docs/handoff/<ola>.md` desde `TEMPLATE.md`. Se ejecuta con Spec Kit
  (`/speckit-specify` referenciando la ola y las O-xx que cierra).
- **Ola 3 incompleta tras #171 (revisado 2026-10-24, #178):** #171 solo cambió `asyncapi.yaml`
  y el handoff; faltaban las rutas REST, sus fixtures y la moderación. Lo resuelve #182 (viñeta
  "Ola 3 REST" de arriba).

---

## Pista F — Frontend Flutter

### T-F01 · AuthBloc real — ✅ HECHA (#46, #56) · P0 · O-20
- **Qué:** quitar el `// MOCK` de `auth_bloc.dart` (`_onLoginRequested`, `_onAuthCheckRequested`)
  y usar `LoginUseCase`/`RegisterUseCase`/`LogoutUseCase` + persistencia de tokens y
  refresh automático en el interceptor Dio (ADR-004). Solo operaciones del contrato:
  `register`, `login`, `refresh`, `logout`.
- **Auto-login:** con lo que hay en contrato, se resuelve con el refresh token guardado
  (`POST /auth/refresh`) y el `user` del `AuthResponse`. `GET /auth/me` **no** se usa
  hasta la Ola 1.
- **Cierre:** sin datos hardcodeados en `features/auth`; tests de bloc para éxito, 401,
  400 y refresh; funciona contra `npm run mock:api`.
- **Complemento (#56):** el splash decidía a ciegas; ahora navega según la sesión
  restaurada, `POST /auth/register` deja de enviar `displayName` (fuera de
  `RegisterRequest`; hallazgo para RFC) y los flujos se verifican contra el mock.

### T-F02 · Retirar `MockApiService` (home, people) — 🔴 BLOQUEADA por T-C07 (Olas 1–2)
- Home necesita eventos (`GET /events` → D-03) y people necesita `social/search` (Ola 2).

### T-F03 · Wiring de blocs y rutas — ✅ HECHA (#63) · P1
- **Qué:** proveer `CheckInBloc` donde se usan sus páginas (o en el `MultiBlocProvider`
  raíz) y registrar la ruta de `ArchiveDetailPage` en `config/routes/app_router.dart`.
- **No hacer:** conectar endpoints nuevos.
- **Cierre:** navegar a check-in y a detalle de archivo no lanza `ProviderNotFound`;
  widget test de cada ruta.
- **Complemento (#63):** `CheckInBloc` se provee en `lib/app.dart` y la ruta
  `archiveDetail` recibe `archiveId` por `arguments`. Hallazgo: la feature archive crea
  tres `ArchiveBloc` distintos (uno por widget) que nunca se cierran.

### T-F04 · `?token=` en los WebSocket — ✅ HECHA (#60) · P0 · CONVENTIONS §7, O-28, ADR-015
- **Qué:** `checkin_repository_impl.dart`, `mosaic_repository_impl.dart`,
  `streaming_repository_impl.dart` construyen la URL desde `Env.wsBaseUrl` (no
  `baseUrl.replaceFirst('http','ws')`) y agregan `?token=<accessToken>`. Manejar cierre
  `4401` → refresh + reconexión una vez.
- **Chat (ADR-015):** `chat_remote_datasource.dart` hace lo mismo y **retira `?userId=`**;
  `connect` deja de recibir el `userId`.
- **Cierre:** test unitario que verifica la URL generada y el manejo de 4401.
- **Complemento (#60):** la lógica común vive en `core/network/ws_connection.dart` y el
  refresh de ADR-004 se extrajo a `core/network/token_refresher.dart` (lo comparten REST y
  WS). Hallazgos fuera de alcance reportados en el PR: ninguna página despacha
  `ConnectToWebSocket` (check-in y mosaico no conectan desde la UI) y
  `ChatBloc._onSendTypingIndicator` conecta en vez de enviar el frame.

### T-F05 · Subida al mosaico — ✅ HECHA (#186) · ADR-016
- **Qué:** subir en dos pasos: `POST /media/upload` y luego `POST /mosaic/post` con el
  `mediaId`. Deja de llamar a `/mosaic/photo` y `/mosaic/checkin`.
- **Excepción A3:** solo `image/jpeg`, `image/png` e `image/webp`; el selector no ofrece video
  ni otros formatos en esta ola.

### T-F08 · Lista "mis eventos" — ✅ HECHA (#181) · ADR-014
- **Qué:** `getMyEvents` envía `scope=mine`; `getUpcomingEvents` deja de llamar a
  `/events/upcoming` (fuera de contrato) y usa `GET /events?scope=upcoming`.
- **Cierre:** tests del datasource contra los fixtures de T-C06.

### T-F06 · Build web estricto — ✅ HECHA (#64) · P1 · O-15
- **Qué:** el build de producción (`deploy-frontend.yml` y/o un check en `Env`) falla si
  `API_BASE_URL` o `WS_BASE_URL` están vacíos; el default `localhost` solo en debug.
- **Cierre:** el job de Vercel falla sin las variables; pasa con ellas.
- **Complemento (#64):** el workflow tiene un paso `Validate production URLs` que corta el
  deploy si faltan, y `Env` ya no cae a `localhost` en release (lanza `StateError`).

### T-F09 · Prueba en dispositivo de `mobile_scanner` y `share_plus` — 🟢 READY (manual) · P0
- **Por qué:** #158 subió `mobile_scanner` de 3 a 7 y `share_plus` de 7 a 10; nadie los probó
  en un teléfono. El escáner QR es el check-in del evento.
- **Qué:** en Android y en iOS (si hay equipo), escanear un QR de check-in real, negar y
  conceder el permiso de cámara, y compartir el link de un evento.
- **Cierre:** resultado por plataforma en el PR o en el issue; si algo falla, issue
  `blocked-by-frontend` antes del release.

### T-F10 · Moderación del mosaico en la app — ✅ HECHA (#185) · P0 · Principio III, T051 (excepción A3)
- **Por qué:** la excepción A3 exige moderación por el organizador. T-B26 la implementa en el
  backend, pero sin una pantalla el organizador no puede usarla durante el evento.
- **Qué:** en el mosaico (grid y feed), solo para el organizador del evento, una acción para
  ocultar o retirar una foto con el endpoint de la Ola 3. La foto desaparece también en los
  demás clientes por el aviso del WebSocket. Post-moderación: no hay cola de aprobación.
- **Cierre:** tests de widget (la acción solo aparece al organizador) y del bloc (la foto sale
  del estado al recibir el aviso de retiro), contra los fixtures de la Ola 3.

### T-F07 · Tests vacíos y cobertura — ✅ HECHA (#65) · P1 · O-19
- **Qué (ajustado 2026-10-22):** la premisa de O-19 está desactualizada:
  `create_event_screen_test.dart` ya tiene 11 casos y `ci.yml` ya corre
  `flutter test --coverage` y sube el artefacto. Lo único falso es
  `test/widget_test.dart`, que monta un `Text('ViTo')` en vez de la app: reemplazarlo por
  un smoke test de `ViToApp` con `di.init()` real (SharedPreferences y secure storage
  mockeados) que compruebe splash → login sin excepciones.
- **Cierre:** ningún archivo de test sin casos; el smoke test monta `ViToApp` y navega.

---

## Pista B — Backend

### T-B01 · Gateway: higiene de `X-User-*` y rate limit — ✅ HECHA (#19) · P0 · O-05, O-27, ADR-007
- **Qué:** en `JwtAuthenticationFilter` (o un `GlobalFilter` previo) eliminar
  `X-User-Id`, `X-User-Email`, `X-User-Roles` de **toda** petición entrante, también en
  rutas públicas, antes de inyectar los propios. `RateLimiterConfig` usa el usuario
  autenticado solo si lo puso el gateway; en rutas públicas usa la IP del cliente.
- **Cierre:** tests: (1) petición pública con `X-User-Id: x` llega al servicio sin ese
  header; (2) dos logins con distintos `X-User-Id` falsos comparten bucket de rate limit.

### T-B02 · Gateway: envelope ADR-003 — ✅ HECHA (#38) · P1 · O-10
- **Qué:** el 401 del filtro y `FallbackController` devuelven
  `{error, message, status, timestamp}` (`UNAUTHORIZED`, `SERVICE_UNAVAILABLE`).
- **Cierre:** test de cada respuesta comparada con el schema `ErrorResponse`.

### T-B03 · ADR-003 en los 6 servicios sin handler — ✅ HECHA (#164) · P1 · O-29, ADR-017
- Servicios sin `@RestControllerAdvice`: social, event, checkin, mosaic, streaming, archive.
- **Qué:** copiar el `GlobalExceptionHandler` y el `ErrorResponse` de `auth-service` (ya
  alineados a ADR-003 por T-B15), adaptando solo el paquete y las excepciones de dominio de
  cada servicio.
- **No hacer:** crear `backend/shared-*` ni cambiar POMs, Dockerfile o CI (es T-B18);
  modificar el handler de auth, chat o media.
- **PR:** declara la desviación del Principio II citando ADR-017 (*Complexity Tracking*).
- **Cierre:** en los 6 servicios, test MockMvc de 400, 404 y 500 con el envelope
  `{error,message,status,timestamp}` y códigos `SCREAMING_SNAKE` de CONVENTIONS.

### T-B04 · Gateway: ruta `/ws/**` — ✅ HECHA (#22, re-entrega de #20) · P0 · O-08, O-28, ADR-006
- **Qué:** rutas `ws://` en el gateway para `/ws/checkin/**`, `/ws/mosaic/**`,
  `/ws/streaming/**`, `/ws/chat/**` hacia cada servicio; validar `?token=` con la misma
  lógica JWT, inyectar `X-User-*` y cerrar con `4401` si es inválido.
- **Cierre:** test de handshake válido / inválido; el frontend conecta vía :8080.

### T-B05 · WebSocketConfig en mosaic y streaming — ✅ HECHA (#25) · P0 · ADR-006, O-28
- **Qué:** registrar `MosaicWebSocketHandler` en `/ws/mosaic/{eventId}` y
  `StreamingWebSocketHandler` en `/ws/streaming/{streamId}` como WS crudo (igual que
  checkin). Sin cambiar los `type` de frame existentes (se documentan en T-C05).
- **Cierre:** test de integración que conecta y recibe un frame.

### T-B06 · Chat a WS crudo — 🔴 BLOQUEADA por T-C05 · P1 · ADR-006, ADR-015, O-28
- Reemplazar STOMP/SockJS de `chat-service` por handler crudo con los frames de
  `asyncapi.yaml`.
- Identidad según ADR-015: `HandshakeInterceptor` que copia `X-User-Id` a la sesión y
  cierra con `4401` si falta; se ignora `userId` en query y frames; cada frame de sala
  verifica que el usuario participe en ella.

### T-B07 · Implementar listado de eventos — ✅ HECHA (#173) · P0 · O-26, ADR-014
- **Qué:** `GET /events?scope=mine|upcoming` en event-service según ADR-014:
  identidad solo de `X-User-Id`; `mine` por defecto; `400 VALIDATION_ERROR` para un
  `scope` desconocido o `size` > 100; `forViewer` aplicado a cada evento listado.
  Índices `{organizerId: 1, createdAt: -1}` y
  `{status: 1, "settings.isPublic": 1, "details.startDate": 1}` en `01-init.js`, retirando
  los de `schedule.start` y `location.coordinates`, que son anteriores a ADR-001.
  Retirar `/my-events` y `/upcoming` cuando T-F08 esté mergeada.
- **Cierre:** tests de los dos scopes, del `400` y de que `upcoming` no incluye eventos
  privados; respuestas iguales a los fixtures de T-C06.

### T-B08 · `hideAddressUntilAccepted` — ✅ HECHA (#26) · P1 · ADR-001, Principio IX, O-33
- **Qué:** el campo existe en contrato, ADR-001 y Flutter, pero no en
  `Event.EventSettings` de event-service. Agregarlo, persistirlo y, en `GET /events/{id}`,
  omitir `details.address`/`latitude`/`longitude` si es `true` y quien consulta no es
  el organizador ni tiene RSVP `ACCEPTED`.
- **Cierre:** tests de dominio para organizador / aceptado / no aceptado; respuesta igual
  al fixture `getEvent` de T-C01.

### T-B09 · FastAPI en compose — ✅ HECHA (#43) · P1 · O-31
- **Qué:** agregar transcode, moderation y recommendation al profile `app` de
  `docker-compose.yml` en los puertos que espera el gateway (8000/8001/8002), con
  healthcheck.
- **Cierre:** `docker compose --profile app up` levanta los 3 y el gateway los alcanza.

### T-B11 · Rate limiter y circuit breaker activos en todos los perfiles — ✅ HECHA (#24) · P0 · O-14, O-27
- **Hallazgo (T-B01):** `application-dev.yml` y `application-docker.yml` redefinen
  `spring.cloud.gateway.routes` completas sin filtros. Spring reemplaza la lista entera, así
  que `RequestRateLimiter` y `CircuitBreaker` de `application.yml` **no se aplican** en
  ningún perfil real (`spring.profiles.active: dev` por defecto; compose usa `docker`).
  Verificado en el log de arranque: cada ruta solo aplica `DedupeResponseHeader`.
- **Qué:** una sola definición de rutas en `application.yml` con URIs parametrizadas
  (`${AUTH_SERVICE_URI:http://localhost:8081}`, …) y los perfiles solo fijan esas variables
  (compose las pasa por entorno). Cierra también los `localhost` del perfil base (O-14).
- **Cierre:** test de contexto que verifica que la ruta `auth-service` tiene
  `RequestRateLimiter` y `CircuitBreaker` con los perfiles `dev` y `docker`.

### T-B10 · Spring Boot y Java unificados — 🔴 BLOQUEADA: requiere spec propio (Principio X) · P1 · O-21, O-35
- 10 poms en 3.2.1, 2 en 3.5.3, Java 17. Planificar con `/speckit-specify`.

### T-B12 · `isPublic` con el nombre del contrato — ✅ HECHA (#28) · P0 · Principio I, ADR-001
- **Hallazgo (T-B08):** `Event.EventSettings` declara `boolean isPublic` con Lombok, que
  genera `isPublic()`/`setPublic()`; Jackson lo expone como **`"public"`**. El contrato
  (ADR-001, CONVENTIONS §6) y Flutter usan `"isPublic"`. Al recibir, Spring Boot ignora la
  propiedad desconocida: **todo evento creado desde la app queda `isPublic=false`** y las
  respuestas traen `"public"`. Verificado con un test de serialización.
- **Qué:** que REST lea y escriba `isPublic` sin acoplar el dominio a Jackson (DTO/mapper
  en `infrastructure`, o mixin de Jackson registrado en config). Revisar el mismo patrón
  `boolean is*` en otros servicios.
- **Cierre:** test que serializa y deserializa `{"settings":{"isPublic":true}}` y fixture
  `createEvent` de T-C01 aceptado tal cual.

### T-B13 · Mismo defecto de `is*` en otros servicios — 🔴 BLOQUEADA por T-C07 (olas 2–5) · P1 · Principio I
- **Hallazgo (T-B12):** los mismos `boolean is*` primitivos con Lombok salen sin el prefijo
  `is` en JSON y se ignoran al recibirlos. El frontend espera el nombre con `is`:
  `EventArchive.EventArchiveSettings.isPublic` (archive), `MosaicFeedItem.isLiked` (mosaic),
  `SocialProfile.isFollowing` / `isFollowedBy` (social), `ChatMessage.isDeleted` (streaming).
  Los `Boolean` envueltos (p. ej. `ArchiveService.*Command.isPublic`) no tienen el problema.
- **Qué:** al contractualizar cada servicio en su ola, aplicar el mismo patrón de T-B12
  (`@JsonMixin` en `infrastructure`) con el nombre que fije el contrato.
- **Cierre:** test `@JsonTest` por servicio contra el fixture de su ola.

### T-B15 · Códigos y errores de auth-service y `createEvent` según los fixtures — ✅ HECHA (#39) · P0 · ADR-003, ADR-005, ADR-009, Principio I
- **Hallazgo (T-I07):** probando el stack de producción, `POST /events` respondió `200`
  (contrato: `201`) y un registro duplicado respondió `400 "Validation Error"`
  (contrato: `409 CONFLICT`). El `GlobalExceptionHandler` de auth-service, citado en el
  AGENT-BRIEF como referencia, no sigue ADR-003: `error` en texto libre ("Validation Error",
  "Authorization Error"…), mensajes en inglés y `timestamp` `LocalDateTime` sin zona.
  T-B03 no lo cubre (solo trata los servicios **sin** handler).
- **Qué:**
  - event-service: `createEvent` → `201 Created`.
  - auth-service: duplicado de email o username → `409` `CONFLICT` (excepción de dominio
    propia, no `IllegalArgumentException`); validación → `400` `VALIDATION_ERROR` con
    `details` por campo; resto de códigos `SCREAMING_SNAKE` de CONVENTIONS; mensajes es-ES;
    `timestamp` ISO-8601 UTC (`Instant`).
- **No hacer:** tocar los servicios de T-B03 ni cambiar el contrato.
- **Desbloquea:** que T-F01 pruebe 400/409/401 contra el backend real y no solo contra Prism.
- **Cierre:** tests MockMvc de `registerUser` (201, 400, 409), `loginUser` (200, 401) y
  `createEvent` (201) comparados con sus fixtures de `docs/api/fixtures/` (mismas claves,
  mismos `status` y `error`).
- **Nota (decisión de leads, 2026-10-17):** donde `openapi.yaml` contradice a ADR-004
  (respuesta 2xx de auth y `logoutUser`), manda ADR-004: logout sigue en `204`. El
  contrato se corrige en T-C08.

### T-B16 · `expiresIn` en segundos y forma de `AuthResponse` según ADR-004 — ✅ HECHA (#40) · P0 · ADR-004, Principio I
- **Hallazgo (T-B15):** `expiresIn` sale en **milisegundos** (`86400000`, el valor de
  `jwt.expiration`); ADR-004 y `openapi.yaml` lo definen en **segundos**.
  La forma de la respuesta (`user` plano, sin `tokenType`) ya cumple ADR-004, pero ningún
  test la fija.
- **Qué:** `expiresIn` en segundos en `register`, `login` y `refresh`; test que fije la forma
  de ADR-004: `{accessToken, refreshToken, expiresIn, user{id, username, email,
  displayName, avatar}}`, sin `tokenType`.
- **No hacer:** cambiar la forma de la respuesta ni `openapi.yaml` (eso es T-C08).
- **Cierre:** test de la respuesta de las tres operaciones con `expiresIn` igual a
  `jwt.expiration / 1000`.

### T-B17 · Fuga de eventos privados en `/events/upcoming` — ✅ HECHA (#153) · P0 · Principio III, ADR-014
- **Hallazgo (D-03):** `EventMongoAdapter.findUpcomingEvents` filtra por
  `status = PUBLISHED` y `details.startDate` ≥ ahora, pero no por `settings.isPublic`.
  Cualquier usuario autenticado lista eventos privados ajenos (título, fechas, descripción).
  La ruta está expuesta hoy por el gateway.
- **Qué:** agregar `settings.isPublic = true` a la consulta y limitar `size` a 100 en
  `/my-events` y `/upcoming`. No cambia rutas ni contrato: es un arreglo de seguridad sobre
  el código actual, previo a T-B07.
- **Cierre:** test de que `/events/upcoming` no devuelve un evento privado publicado y
  futuro de otro organizador; test de `size` > 100.
- **Bloquea el primer release** (T051, A8).

### T-B18 · Librería `backend/shared-web` — 🔴 BLOQUEADA por la implementación de la Ola 1 · P1 · O-23, ADR-017
- **Qué:** parent POM Maven multi-módulo en `backend/` con el módulo `shared-web`
  (`JwtAuthenticationFilter`, `ErrorResponse`, handler base de ADR-003). Los servicios lo
  consumen como dependencia; `backend/Dockerfile` usa `backend/` como contexto; los jobs
  `backend` y `docker` de `ci.yml` y la matriz de `cd.yml` construyen desde el parent.
  Se borran todas las copias.
- **Coordinar con T-I12:** los filtros `paths` de cada servicio incluyen `backend/shared-web/**`.
- **Plazo (ADR-017):** se toma antes de implementar la Ola 2. Se adelanta si aparece una
  tercera clase compartida.
- **Cierre:** ninguna clase duplicada entre servicios (`JwtAuthenticationFilter`,
  `ErrorResponse`, handler); CI y build de imágenes verdes para los 10 servicios.

### T-B19 · Validación de subida en media-service — ✅ HECHA (#155) · P0 · Principio III, CONVENTIONS §8, T051 (A1, A2)
- **Hallazgo:** `MediaServiceImpl.validateFile` confía en el `Content-Type` que manda el
  cliente, acepta `image/gif`, `video/webm` y `audio/mpeg` (fuera del contrato) y el límite
  es de 100 MB (`media.max-file-size` y `spring.servlet.multipart.*`).
- **Qué:** límite de 10 MB en los dos lugares; lista blanca `image/jpeg`, `image/png`,
  `image/webp` y `video/mp4` (CONVENTIONS §8); el tipo se detecta por los magic bytes del
  archivo (p. ej. Apache Tika) y debe coincidir con el declarado. El error es `400` con el
  envelope de ADR-003, como ya dice `openapi.yaml`. No cambia el contrato.
- **Cierre:** tests de un PNG renombrado como JPEG (acepta con el tipo real o rechaza si no
  coincide), un HTML con `Content-Type: image/png` (400), un GIF (400) y un archivo de 11 MB
  (400).

### T-B20 · Orígenes del WS de chat desde configuración — ✅ HECHA (#163) · P0 · T051
- **Hallazgo:** `chat-service` tiene los orígenes fijos en `localhost` en
  `WebSocketConfig` (`setAllowedOriginPatterns`) y en `application.yml`; en producción la
  web no podría conectarse.
- **Qué:** leer los orígenes de `CORS_ALLOWED_ORIGINS`, como el gateway, y pasar la variable
  a `chat-service` en `docker-compose.prod.yml`. Revisar lo mismo en los `WebSocketConfig`
  de mosaic y streaming (T-B05).
- **Cierre:** test de que un origen fuera de la lista recibe `403` en el handshake y uno de
  la lista conecta.

### T-B21 · Revocación inmediata de access tokens — ✅ HECHA (#168) · P1 · T051 (M1)
- **Hallazgo:** al rotar o cerrar sesión, el refresh token va a la lista negra de Redis
  (`AuthServiceImpl`), pero el access token sigue valiendo hasta que vence (15–30 min).
- **Qué:** `logoutUser` agrega el `jti` del access token a Redis con TTL igual a su vida
  restante; el filtro JWT del gateway rechaza los `jti` de la lista con `401`. Si el token no
  tiene `jti`, agregarlo al emitirlo. No cambia el contrato (`401` ya está en las rutas
  protegidas).
- **Cierre:** test de que un access token usado después de `logout` recibe `401` en el
  gateway; con Redis caído, el gateway no deja de validar firmas (decidir y documentar si
  falla abierto o cerrado).

### T-B22 · Throttling de login por cuenta — 🟡 RFC · P1 · T051 (M2)
- **Hallazgo:** solo hay rate limit por IP (T-B01, T-B11). Un ataque distribuido de
  credential stuffing contra una cuenta no se frena. `loginUser` no documenta `429` en
  `openapi.yaml`, aunque el gateway ya lo devuelve por IP.
- **RFC:** agregar `429` con `Retry-After` a `loginUser` y `registerUser` (CONVENTIONS §9),
  con fixture.
- **Qué (después del RFC):** contador de intentos fallidos por correo en Redis; tras 5
  fallos, espera creciente (1, 2, 4… hasta 15 min) con `429`. Sin bloqueo duro: un bloqueo
  permitiría dejar fuera a un usuario ajeno. La respuesta no revela si la cuenta existe.
- **Cierre:** test del sexto intento con `429` y `Retry-After`, y de que un login correcto
  reinicia el contador.

### T-B23 · Rate limit por usuario — ✅ HECHA (#180) · P1 · T051 (M3)
- **Qué:** en las rutas autenticadas del gateway, el `KeyResolver` usa el `X-User-Id` que
  el propio gateway deriva del JWT (nunca el que manda el cliente, T-B01), con los límites de
  CONVENTIONS §9. Las rutas públicas siguen por IP.
- **Cierre:** test de que dos usuarios desde la misma IP tienen buckets distintos y de que un
  `X-User-Id` falso del cliente no cambia el bucket.
- **Hallazgo (#180):** detrás de Caddy, la IP de la conexión es siempre la de Caddy, y las rutas
  públicas compartían un solo bucket para todo internet. `vito.rate-limit.trusted-proxies` (1 en
  `prod`) toma la IP de `X-Forwarded-For`. Si `api.` deja de estar en *DNS only*, hay que revisarlo.
  Los handshakes `/ws/**` siguen sin rate limit.

### T-B24 · Audit log de seguridad — 🟢 READY · P1 · Principios VI y IX, T051 (M6)
- **Qué:** auth-service registra en un logger `AUDIT` (JSON, T053 de la feature 001):
  login correcto y fallido, logout, refresh rechazado, cambio de contraseña y borrado de
  cuenta. Campos: `event`, `userId` (si existe), `ip` truncada a /24, `traceId`. Nunca el
  correo, la contraseña ni el token (Principio VI).
- **Cierre:** test de que cada evento produce una línea con los campos y sin datos
  prohibidos; consulta de ejemplo en Loki en `infrastructure/observability/README.md`.

### T-B25 · Re-codificar las imágenes subidas — ✅ HECHA (#174) · P0 · Principio III, T051 (excepción A3)
- **Por qué:** la excepción A3 (aprobada en #169) publica la subida al mosaico sin antimalware
  a cambio de re-codificar cada imagen en el servidor antes de servirla.
- **Qué:** en `POST /media/upload` (ya en el contrato), después de la validación de T-B19:
  decodificar `jpeg`/`png`/`webp`, rechazar lo que no decodifica o supera un tope de píxeles
  (bomba de descompresión) con `400 VALIDATION_ERROR`, re-codificar y guardar solo la versión
  re-codificada, sin metadatos (EXIF, GPS incluido: Principio IX). No cambia el contrato.
- **Sin video (excepción A3):** rechazar `video/mp4` con `400 VALIDATION_ERROR` aunque
  CONVENTIONS §8 todavía lo liste. Sacarlo de §8 le toca a `contract/ola-3` (T-C07).
- **Cierre:** tests con una imagen con EXIF/GPS (la salida no los tiene), un `video/mp4`
  (`400`), un políglota
  (imagen válida con datos anexados: se eliminan), una imagen corrupta y una bomba de
  descompresión (las dos `400`); límite de memoria del servicio respetado
  (`docker-compose.prod.yml`).

### T-B26 · Moderación por el organizador — ✅ HECHA (#189) · P0 · Principio III, T051 (excepción A3)
- **Qué:** el organizador del evento oculta o retira un elemento del mosaico; deja de
  aparecer en feed, grid y WebSocket. Solo el organizador (autorización por recurso). El
  endpoint lo define la rama `contract/ola-3`.
- **Post-moderación (leads, 2026-10-24):** los elementos se publican al instante; no hay
  estado "pendiente de aprobación". La pantalla del organizador es T-F10.
- **Cierre:** tests de autorización (`403` a quien no organiza), de que el elemento no sale
  en feed ni grid y de que el WS avisa el retiro.

### T-B27 · Logs en INFO en producción — ✅ HECHA (#175) · P1 · Principio VI
- **Por qué:** los 9 servicios Java (todos menos el gateway, que lo resolvió #170) fijan
  `com.vito` y casi todos `org.springframework.security` en DEBUG en `application.yml`, sin un
  perfil `prod` que lo baje. Con el tope de logs por contenedor, el log rota en minutos.
- **Qué:** `application-prod.yml` por servicio con esos loggers en INFO (el patrón de #170).
- **Cierre:** un test por servicio con los perfiles de `docker-compose.prod.yml` que verifique
  que no hay DEBUG.

### T-B28 · Errores de Spring con envelope en auth, chat y media — 🟢 READY · P1 · ADR-003, ADR-017
- **Por qué (hallazgo de T-B03, #164):** el handler de esos tres servicios convierte en `500`
  una ruta inexistente o un método no soportado; las copias de los otros 6 conservan el
  status de Spring. ADR-017 pide que las copias no diverjan. Además, los `401`/`403` que
  genera Spring Security no pasan por el handler en ningún servicio.
- **Qué:** llevar a auth, chat y media la rama de T-B03 que respeta el status de las
  `ErrorResponse` 4xx de Spring, y un `AuthenticationEntryPoint`/`AccessDeniedHandler` que
  responda con el envelope de ADR-003.
- **Cierre:** tests de ruta inexistente (`404`), método no soportado (`405`) y petición sin
  token (`401`) con envelope en los tres servicios.

### T-B29 · Validación del cuerpo en `createEvent` — 🟢 READY · P1 · CONVENTIONS §3.3
- **Por qué (hallazgo de T-B03):** `createEvent` no tiene `@Valid`, así que nunca produce el
  `400` "El campo title es obligatorio" del fixture `createEvent.400`.
- **Cierre:** test que reproduce el fixture.

### T-B30 · Firma asimétrica de JWT — 🔴 BLOQUEADA: requiere spec propio (Principio X) · P1 · Principio III
- **Por qué (hallazgo de T-B21, #168):** los tokens se firman con HS256 y un secreto que
  comparten los 10 servicios; la constitución pide firma asimétrica y JWKS. Comprometer un
  servicio permite emitir tokens.
- **Qué (a especificar):** RS256 o ES256, JWKS publicado por auth, rotación de claves y
  convivencia con los tokens vigentes. Relacionado: el refresh no revoca el access token
  anterior (el request no lo trae; requiere RFC al contrato) y las copias de
  `JwtAuthenticationFilter` no consultan la revocación (se resuelve con T-B18).

### T-B31 · Spring Boot con soporte en los 10 servicios Java — 🔵 EN CURSO (backend/t-b31-spring-boot-soporte, spec; Principio X) · P0 · Principio III, excepción A9
- **Por qué:** el primer escaneo real de Trivy (release 0.1.0) encontró de 39 a 57 CVE
  HIGH/CRITICAL con arreglo por imagen Java: Spring Boot 3.2.12 ya no tiene soporte. Algunos
  CRITICAL (`spring-webmvc`, `spring-webflux`) solo tienen arreglo en Spring Framework 7. La
  excepción A9 deja Trivy en modo informe **hasta el 2026-11-07**; desde el 2026-11-08
  vuelve a bloquear todo despliegue.
- **Qué:** spec corta con Spec Kit (absorbe T-B10, versiones unificadas): versión destino
  (Spring Boot 4.0.x con Spring Cloud compatible para el gateway, o la 3.5.x más reciente si
  cubre los CRITICAL), pasos de migración por servicio y riesgos (Jackson 3, Jakarta, Spring
  Security 7, propiedades del gateway). Luego la migración, con un parent POM si T-B18 ya
  existe.
- **Cierre:** los 10 servicios en la versión destino, CI verde y un `cd.yml` con Trivy
  bloqueante sin CVE HIGH/CRITICAL con arreglo fuera de `.trivyignore`. Se cierran la
  excepción A9 y T-B10.

### T-B32 · Fan-out en vivo de reacciones y mensajes del streaming — 🟡 RFC · P1 · Principio III, ADR-006, O-28
- **Por qué:** una reacción enviada por REST no llega en vivo a ningún espectador. Revisado
  contra el código: `StreamingServiceImpl.addReaction` guarda en Mongo y publica
  `STREAM_REACTION` en Kafka, pero no hay ningún `@KafkaListener` en `backend/`, así que nadie
  lo consume. Por WS, `StreamingWebSocketHandler` guarda las sesiones en un `ConcurrentHashMap`
  en memoria y reenvía el frame `REACTION` solo a las sesiones de esa misma instancia. Con más
  de una réplica, o en un cruce REST→WS, la reacción se pierde. `publishChatMessage` y
  `publishViewerCount` parecen tener el mismo problema (por revisar en esta tarea).
- **Qué:** un único canal de fan-out para todas las rutas de entrada (REST y WS), con Redis
  Pub/Sub, como indica el diseño de la Fase 6 de AGENTS.md. Usar `chat-service`
  (`RedisCacheAdapter`) como referencia. Revisar en la misma tarea los eventos `chat` y
  `viewerCount`.
- **Contrato (bloqueante):** `docs/api/openapi.yaml` no tiene ruta REST de reacciones; solo
  aparece `REACTION` como valor de enum. `asyncapi.yaml` sigue en RFC por T-C05. Antes de
  implementar hay que abrir un issue `contract-change` que defina la ruta y el frame.
- **Cierre:** una reacción enviada por REST o por WS llega a todos los espectadores del stream
  en menos de 1 s, aunque estén en otra instancia. Hay un test de integración que lo prueba con
  dos instancias o con dos sesiones en réplicas distintas. Se cumple el KPI "Latencia
  reacciones stream < 1 s" y el criterio MVP n.º 9.

---

## Pista I — Infra, CI/CD e higiene

### T-I01 · Higiene del repo — 🟢 READY · P2 · O-23, O-25, O-34
- **Qué:** agregar `backend/out/` al `.gitignore`; borrar `backend/java/` (copia huérfana
  de `com/vito/gateway`) tras confirmar que ningún pom/Dockerfile lo referencia.
  Listar en el PR qué hacer con `serve.js`, `Creación`, `img/` y el `.docx` de la raíz
  (mover a `docs/` o eliminar) y esperar OK del lead antes de borrarlos.
- **Cierre:** `git status` limpio tras un build. (La cola UTF-16 del README raíz ya se corrigió en el PR de la constitución v1.1.0.)

### T-I02 · `integration-tests/` — 🟢 READY · P2 · O-19
- **Qué:** hoy solo tiene `src/` sin `pom.xml`. Darle `pom.xml` con Testcontainers y un
  job de CI manual, o retirarlo. Proponer en el PR.
- **Ojo (hallazgo de T-B21):** usa campos anteriores a ADR-004 (`token` en vez de
  `accessToken`).

### T-I03 · `architecture.md` coherente — 🟢 READY · P2 · O-36
- **Qué:** el esquema `events` usa la forma plana (`name`, `date`, `isPublic`…) que
  ADR-001 eliminó; faltan checkin/mosaic/archive en los diagramas. Alinearlo con ADR-001,
  `endpoint-inventory.md` y marcar Dragonfly/Eureka/S3 como "en revisión (D-02)".

### T-I04 · CD por servicio — ⚪ REEMPLAZADA por T-I08 (ADR-011)
- `cd.yml` construye auth sin `file: backend/Dockerfile`, con el mismo nombre de imagen que
  transcode, y los deploy son `echo`. ADR-011 elimina el registro de imágenes: se rehace
  completo en T-I08.

### T-I05 · Kubernetes / despliegue — ⚪ DIFERIDA a Fase Crecimiento (ADR-011)
- Los manifiestos de `infrastructure/kubernetes/` no se mantienen en Fase Tracción. No
  borrarlos: se retoman al cumplir el disparador de ADR-011.

### T-B14 · Actuator, health y métricas en todos los servicios — ✅ HECHA (#33) · P0 · O-11, Principio VI
- **Qué:** `spring-boot-starter-actuator` + `micrometer-registry-prometheus` en los 10
  servicios Java, con `/actuator/health/liveness`, `/actuator/health/readiness` y
  `/actuator/prometheus` (no expuestos por el gateway). En transcode y moderation, endpoint
  `/health` y métricas con `prometheus-client`.
- **Por qué ahora:** los healthchecks de `docker-compose.prod.yml` (T-I07) y Grafana Cloud
  (T-I13) dependen de esto.
- **Cierre:** test por servicio que verifica `200` en liveness/readiness y que
  `/actuator/prometheus` responde.

### T-I06 · Aprovisionar cuentas y VPS — ✅ HECHA (manual, 2026-10-24) · P0 · ADR-011, ADR-013
- **Avance (2026-10-22):** listos el VPS Contabo 12 GB, los buckets R2, Grafana Cloud y el
  correo de equipo (Gmail). Faltan la llave SSH, el Environment `production`, el
  dominio/DNS/Pages y Resend (ADR-013, actualización del 2026-10-22).
- **Avance (2026-10-24):** listos los Environments `production`, `production-hotfix` (con
  revisores) y `production-preflight`, los secretos SSH y de Cloudflare, las variables
  `VITO_*` y `PAGES_PROJECT`, y el DNS de `api.`, `live.` y `media.`.
- **Cierre (2026-10-24):** `bootstrap.sh --check-only` con todo OK (llave SSH, sin
  contraseñas, `docker compose` instalado); los tres Environments con los 10 secretos y las 4
  variables; secretos de Telegram en el repo; CORS de `vito-media` verificado con `curl`;
  Secret scanning y Push protection activos; rama por defecto `develop`; gastos en la hoja.
  La huella SSH se verificó desde dos redes distintas, no por la consola de Contabo.
  **Pendientes menores (no bloquean el release):** Alex como administrador en Cloudflare y
  en Grafana Cloud, Email Routing `infra@<dominio>` y Resend (ADR-013, actualización del
  2026-10-24).
- **Tarea manual de los leads** (no la ejecuta un agente):
  0. En GitHub, cambiar la rama por defecto a `develop` (ADR-012).
  1. Correo de equipo, gestor de contraseñas con 2FA, tarjeta y hoja de gastos (ADR-013).
  2. Contabo Cloud VPS 12 GB en EE. UU.; acceso solo por llave SSH, sin login de root por
     contraseña; firewall con 22 (restringido), 80, 443, 1935 (RTMP) y los puertos UDP de
     WebRTC de SRS.
  3. Cloudflare: dominio, DNS, bucket R2 (`vito-media`, `vito-backups`) y token de API
     limitado a esos buckets.
  4. GitHub Environment `production` **con revisores obligatorios (los leads)**, `SSH_HOST`,
     `SSH_USER`, `SSH_KEY`, `SSH_KNOWN_HOSTS` y los secretos de `.env.prod.example`.
  5. Completar el inventario de cuentas de ADR-013.
- **Cierre:** `ssh` con llave funciona, `docker compose version` en el VPS, buckets creados
  y secretos cargados en GitHub. Registrar el gasto en la hoja de D-09.

### T-I07 · `docker-compose.prod.yml` — ✅ HECHA (#36) · P0 · O-13, ADR-011
- **Qué:** archivo Compose de producción, separado del de desarrollo:
  - los servicios de ADR-011 (10 Java, transcode, moderation, Mongo, Redis, Kafka KRaft de un
    broker, SRS, Caddy); **sin** recommendation ni SeaweedFS;
  - `mem_limit` por servicio y `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75` (o equivalente)
    para que el total quepa en 12 GB con margen; documentar la tabla de memoria;
  - imágenes con tag `${IMAGE_TAG}` (SHA), nunca `:latest`;
  - solo Caddy (80/443) y SRS publican puertos; Mongo, Redis y Kafka con autenticación y
    sin puertos en el host;
  - `Caddyfile`: TLS automático, `api.<dominio>` → gateway (incluido WebSocket);
  - healthchecks usando T-B14.
- **Cierre:** `docker compose -f docker-compose.prod.yml config` válido;
  `docker compose ... up` en local con un `.env` de prueba levanta todo dentro de 12 GB
  (`docker stats` en el PR).

### T-I08 · Pipeline de despliegue — 🔵 EN CURSO (despliegue verificado; falta ensayar el rollback) · P0 · O-12, ADR-011, ADR-012
- **Qué:** rehacer `cd.yml`: al entrar un commit a `main`, construir solo las imágenes de
  los servicios cambiados (tag = SHA), copiarlas al VPS con `docker save | ssh docker load`,
  ejecutar `docker compose up -d` con el nuevo `IMAGE_TAG` y verificar los healthchecks.
  Rollback manual con `workflow_dispatch` indicando un SHA anterior.
- **Avance (verificado en GitHub, 2026-10-28):** preflight con `mode=preflight` en
  `production-preflight` (run 40146636934, `success`, 2026-10-25). Despliegues a `production-hotfix`
  exitosos: run 40159629811 (#194, tag `v0.1.0`, 2026-10-25) y run 40438818788 (#199, 0.1.1,
  2026-10-27). Pendiente: el hotfix #201 (cabeceras de la web) no salió; su aprobación venció
  (`Expire Approvals`) y el despliegue quedó en `error`.
- **Cierre:** un merge a `main` despliega sin intervención (cumplido); el rollback a un SHA anterior
  funciona y queda documentado en el runbook (pendiente: `rollback.yml` no tiene ninguna ejecución).
- **Diseño y tareas:** [`specs/001-cd-produccion`](../../specs/001-cd-produccion/tasks.md).
  El triage de seguridad (T051) exige Trivy, gitleaks y Dependabot (T048–T050) antes del
  primer release.

### T-I09 · Backup de Mongo a R2 — ✅ HECHA (#35) · P0 · Principio IX, ADR-011
- **Qué:** contenedor o cron que corre `mongodump --gzip` diario y sube el archivo a un
  bucket S3 (R2 en producción) con retención de 14 días. Script de restauración.
- **Verificación local:** contra SeaweedFS (API S3) en el compose de desarrollo.
- **Cierre:** el backup se genera y sube; la restauración en una base vacía recupera los
  datos (pasos y salida en el PR).

### T-I10 · media-service con endpoint S3 configurable — ✅ HECHA (#34) · P0 · O-32, ADR-011
- **Qué:** endpoint, región, bucket y credenciales S3 por variables de entorno, con
  path-style configurable. Sin cambios de contrato. Igual para transcode y archive si
  acceden al almacenamiento.
- **Cierre:** test de integración contra SeaweedFS local; documentación de las variables
  para R2 en `.env.example`.

### T-I11 · Frontend web en Cloudflare Pages — 🔵 EN CURSO (#179 · falta verificar en prod) · P1 · ADR-011
- **Desbloqueo (2026-10-24):** T-I06 dejó listos el proyecto de Pages `vito-web` con el dominio
  `app.`, `CLOUDFLARE_API_TOKEN` y `CLOUDFLARE_ACCOUNT_ID`, y las variables `VITO_API_BASE_URL`,
  `VITO_WS_BASE_URL`, `PAGES_PROJECT` y `VITO_MEDIA_ORIGIN` (origen de `img-src` en la CSP) en
  los tres Environments.
- **Avance (#179, en `develop`):** T030–T032 de la feature 001: `web/_redirects`,
  `web/_headers`, `infrastructure/deploy/render-web-headers.sh` (con tests bats), verificación
  del build web en CI y `deploy-frontend.yml` a Pages; `VITO_MEDIA_ORIGIN` agregada al contrato
  de workflows. Fuentes de respaldo: se permite `fonts.gstatic.com` en `font-src` y
  `connect-src`. Queda después del merge a `main`: el cierre de abajo y T033 (retirar Vercel).
- **Requisito de infraestructura (lead):** política CORS de `vito-media` con el origen de la
  web (runbook de aprovisionamiento, paso 5). Sin ella, la web no carga las imágenes.
- **Qué:** reemplazar el deploy a Vercel de `deploy-frontend.yml` por Cloudflare Pages
  (`wrangler pages deploy`), con las mismas puertas de calidad y el build estricto de T-F06.
- **Cabeceras de la web (separadas de T-I18 el 2026-10-23):** van en el `_headers` de T031 de
  la feature 001 (despliegue de T032): `Strict-Transport-Security` y una CSP compatible con
  Flutter: `default-src 'self'`, `script-src 'self' 'wasm-unsafe-eval'`, `connect-src` con la
  API y el WS, `img-src` con `media.`, `frame-ancestors 'none'`. CanvasKit y las fuentes se
  sirven desde el propio sitio (`flutter build web --no-web-resources-cdn`). Como el dominio no
  va en el repo, `deploy-frontend.yml` completa los orígenes de la CSP desde `vars.*` al generar
  `_headers`. Ojo: el motor web de Flutter descarga las fuentes de respaldo (Noto) de
  `fonts.gstatic.com` aunque se use `--no-web-resources-cdn`; o se permite ese origen en
  `font-src`/`connect-src` o se sirve desde el sitio con `fontFallbackBaseUrl`.
- **Cierre:** un merge a `main` publica la web en Pages con `API_BASE_URL`/`WS_BASE_URL` de
  producción; `curl -I` muestra HSTS y la CSP en la web, que carga sin violaciones de CSP en la
  consola del navegador (quickstart, paso 6).

### T-I12 · CI incremental — 🟢 READY · P1 · ADR-011
- **Qué:** que `ci.yml` construya y pruebe solo los servicios cuyos archivos cambiaron
  (filtros `paths` o `dorny/paths-filter`), más un job semanal completo. El repo es público y
  Actions no tiene cuota de minutos (ADR-011, actualización del 2026-10-22): el objetivo es
  feedback rápido.
- **Cierre:** un PR que solo toca `frontend/` no ejecuta jobs de backend, y viceversa.

### T-I13 · Observabilidad con Grafana Cloud — 🟢 READY · P1 · Principio VI
- **Avance (2026-10-24, mínimo para el evento):** `https://api.<dominio>/healthz` (T037 de la
  feature 001, hotfix #192) expone solo la readiness del gateway para el check de uptime de
  Grafana Cloud, con aviso a Telegram. Falta lo demás: Alloy, OTel, logs JSON, tablero y
  alertas (T034–T036, T038–T041, T053).
- **Qué:** Grafana Alloy (u OTel Collector) en el VPS que envía métricas, logs y trazas a
  Grafana Cloud free; dashboard RED del gateway; alertas de caída y de disco/RAM > 80 % a
  ambos leads; monitor de uptime externo. Alertas de picos de `401` y `429` en el gateway
  (T051, M7).
- **Incluir (hallazgos de #167 y #168):** alerta si falta el backup diario (hoy un fallo
  solo queda en el log) y alerta sobre `vito.gateway.revocation.check.errors` (el gateway
  falla abierto si Redis no responde).
- **Cierre:** dashboard con datos reales y una alerta de prueba recibida.

### T-I14 · Runbooks — 🔵 EN CURSO (#167 · falta el ensayo manual) · P1 · Principio IX
- **Qué:** `docs/runbooks/restore.md` (reconstruir el VPS desde cero con Compose + `.env` +
  backup de R2) y `docs/runbooks/incident.md` (qué mirar, cómo hacer rollback, a quién avisar).
- **Cierre:** un ensayo de restauración completo en una máquina limpia, con tiempos medidos
  (RTO ≤ 4 h).
- **Avance:**
  - Runbooks: ✅ #167 ([restore.md](../runbooks/restore.md), [incident.md](../runbooks/incident.md)).
  - Ensayo de restauración (tarea manual de un lead, requiere SSH y secretos): pendiente.
    Se hace con `restore.md` C.2 y sus tiempos se anotan en el PR o issue que lo cierre.

### T-I15 · Protocolo de bloqueos entre agentes — 🟢 READY · P1 · CONTRIBUTING (cadencia)
- **Por qué:** el agente frontend y el backend/infra trabajan en paralelo. Ya existen las
  etiquetas `blocked-by-backend` / `blocked-by-frontend` y el sync de los miércoles, pero
  ningún documento le dice a un agente cuándo y cómo usarlas, ni cómo pedir que se adelante
  una tarea de la otra pista.
- **Qué:**
  - AGENT-BRIEF §9: si una tarea depende de otra pista, el agente abre un issue con la
    etiqueta `blocked-by-<pista>` que cita la tarea bloqueada y la que propone adelantar
    (T-xx), el impacto y si hay un rodeo temporal (p. ej. Prism). No implementa la otra pista.
  - Plantilla de issue `.github/ISSUE_TEMPLATE/blocked.md` con esos campos.
  - AGENT-BRIEF §7: al tomar una tarea, revisar primero los issues `blocked-by-<mi pista>`
    abiertos; atenderlos tiene prioridad sobre las tareas P1/P2 de la propia pista.
  - Los leads repriorizan en `tasks.md` en el sync del miércoles; el agente no cambia la
    prioridad de tareas ajenas.
- **Cierre:** la plantilla aparece al crear un issue en GitHub; AGENT-BRIEF y CONTRIBUTING
  enlazan el flujo; un issue de ejemplo (T-F01 ← T-B02/T-B15) creado con la plantilla.

### T-I16 · Protección de rama en `main` y `develop` — ✅ HECHA (#49, manual) · P0 · ADR-012, CONTRIBUTING §1
- **Tarea manual de los leads** (requiere admin del repo; no la ejecuta un agente).
- **Por qué:** el repo pasó a ser público el 2026-10-19 y el plan Free ya permite la
  protección de rama. Hasta ahora, "sin push directo" y "1 aprobación de CODEOWNER" se
  cumplían solo por acuerdo, y #20 y #46 terminaron mergeados fuera de `develop`.
- **Qué:** aplicar en las dos ramas las reglas de CONTRIBUTING §1: PR obligatorio también
  para admins, 1 aprobación de CODEOWNER, los 25 checks de `ViTo CI`, sin force push ni
  borrado.
  - `develop`: ✅ aplicada el 2026-10-19.
  - `main`: ✅ aplicada el 2026-10-19; reglas idénticas a las de `develop`.
  - Documentación: ✅ #49.
- **Cierre:** `gh api repos/AppViTo/ViTo/branches/main --jq .protected` devuelve `true` y
  las reglas de `main` coinciden con las de `develop`.

### T-I17 · Rutas de subida cerradas en producción — ✅ HECHA (#156) · P0 · Principio III, T051 (A3)
- **Por qué:** la constitución exige antimalware y moderación antes de publicar en el
  mosaico, y no existen. La excepción A3 del triage cierra la subida en producción hasta la
  ola del mosaico (Ola 3).
- **Qué:** en el perfil `prod` del gateway, `POST /api/v1/media/**` y los métodos de
  escritura de `/api/v1/mosaic/**` responden `404` con el envelope de ADR-003, sin llegar a
  los servicios. En `dev` y `docker` siguen abiertas.
- **Cierre:** test de perfil (como `RouteFiltersProfileTest`) que compruebe el `404` en
  `prod` y el enrutamiento en `docker`. Se revierte en la Ola 3, junto con el antimalware y
  la moderación, y se cierra la excepción A3.

### T-I18 · Cabeceras de seguridad — ✅ HECHA (#157) · P0 · Principio III, T051 (A4)
- **Qué:** `infrastructure/caddy/Caddyfile`: en `api.` y `live.`,
  `Strict-Transport-Security: max-age=31536000; includeSubDomains`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` y quitar `Server`.
- **Alcance (2026-10-23):** la parte web (CSP y HSTS en Pages) pasó a T-I11. La web sigue en
  Vercel, que ignora `_headers`, y el cierre en el navegador requiere Pages (bloqueada por T-I06).
- **Cierre:** `infrastructure/caddy/tests/security-headers.sh` (job Deploy Scripts de CI)
  levanta el Caddyfile real con backends falsos y comprueba con `curl` las cabeceras en
  `api.` y `live.`, también en lo que llega por `reverse_proxy`.

### T-I19 · Un usuario de Mongo por servicio — 🟢 READY · P1 · Principios II y III, T051 (M12)
- **Hallazgo:** los 10 servicios Java usan el usuario root (`MONGODB_URI` en
  `x-java-service` de `docker-compose.prod.yml`). Si se compromete uno, quedan expuestas
  todas las bases, y nada hace cumplir el Principio II.
- **Qué:** script idempotente `infrastructure/deploy/remote/ensure-service-users.sh` (mismo
  patrón que `ensure-ro-user.sh` de la feature 001) que crea `vito_<svc>` con `readWrite`
  solo en la base del servicio; contraseñas `MONGO_<SVC>_PASSWORD` en el `.env`; un
  `MONGODB_URI` por servicio en el compose. Root queda solo para `mongo-backup` y los
  scripts de operación.
- **Cierre:** cada servicio arranca sano con su usuario; un test de integración comprueba
  que el usuario de event-service no puede leer la base de auth (`vito_db`). Si algún servicio lee la base de otro, se
  registra como deuda del Principio II.

### T-I20 · Kafka con TLS y ACL por tópico — ⚪ DIFERIDA (Fase Crecimiento) · P2 · Principio III, T051 (A7)
- **Excepción A7:** Kafka usa `SASL_PLAINTEXT` con un solo usuario y sin ACL. Se acepta
  mientras broker y clientes estén en el mismo host, en la red interna de Compose y sin
  puertos publicados (ADR-011 b).
- **Vuelve cuando:** Kafka salga del host (Fase Crecimiento) o el 2027-04-22, lo que pase
  primero.
- **Qué:** listener `SASL_SSL`, un usuario por servicio y ACL por tópico según los
  productores y consumidores de `asyncapi.yaml`.

### T-I21 · Subida al mosaico en producción detrás de una propiedad — ✅ HECHA (#177, #183) · P0 · Principio III, T051 (excepción A3)
- **Por qué:** T-I17 cierra la subida en `prod` por perfil. La excepción A3 la abre solo para
  imágenes re-codificadas y moderables.
- **Qué:** `ClosedUploadRoutesFilter` pasa a depender de una propiedad (cerrada por defecto),
  que se activa con una variable de `.env.prod.example`. Con la propiedad activa: abre
  `POST /api/v1/media/upload` y las escrituras del mosaico que estén en el contrato de la
  Ola 3; lo demás sigue cerrado.
- **Activación en producción:** solo cuando T-B25 y T-B26 estén desplegadas.
- **Cierre:** tests con la propiedad apagada (lo de T-I17 sigue igual) y encendida.

### T-I22 · Reconstruir todas las imágenes desde el CD — 🟢 READY · P1 · ADR-011, Principio IX
- **Por qué (hallazgo de T-I14, #167):** `cd.yml` construye solo lo que cambió desde el
  último despliegue exitoso. En un VPS vacío o reconstruido, `deploy.sh` sale con código 4
  por imágenes faltantes; hoy el rodeo de `docs/runbooks/restore.md` compila en el VPS, sin
  Trivy.
- **Qué:** input de `workflow_dispatch` (p. ej. `rebuild_all`) que fuerza la matriz completa
  sin cambiar el flujo normal. Actualizar `restore.md` y `contracts/workflows.md` de la
  feature 001.
- **Cierre:** test de `changed-services.sh` (o del paso de la matriz) con la opción activa;
  actionlint verde.

### T-I23 · Recomendaciones sin servicio en producción — 🟢 READY · P2 · ADR-011
- **Por qué (hallazgo de T-I14):** el contrato publica `GET /recommendations/events` y el
  gateway tiene la ruta, pero ADR-011 deja `recommendation-service` fuera de
  `docker-compose.prod.yml`. En `prod` la llamada cae siempre en el fallback.
- **Qué:** verificar que el fallback responde con el envelope de ADR-003 y que la app degrada
  sin error visible (sección oculta o vacía).
- **Cierre:** test del fallback en el gateway y prueba en la app.

### T-I24 · Registro de imágenes — ⚪ DIFERIDA (Fase Crecimiento) · P2 · ADR-011
- **Por qué (hallazgo de T-I14):** las imágenes viajan como artifacts y solo existen en el
  VPS. Perderlo obliga a recompilar las 13, y eso es lo que más pesa en el RTO.
- **Revisión:** con la Fase Crecimiento o si el ensayo de T-I14 supera el RTO.

