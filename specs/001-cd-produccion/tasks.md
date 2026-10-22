---
description: "Tareas de la feature 001: despliegue continuo a producción (Fase Tracción)"
---

# Tasks: Despliegue continuo a producción (Fase Tracción)

**Input**: Design documents from `specs/001-cd-produccion/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: incluidos. La constitución (§ Flujo SDD, punto 4) exige siempre tareas de pruebas
(IV), observabilidad (VI) y despliegue (VIII). Las pruebas de scripts usan `bats` y
`shellcheck`; las de punta a punta, [quickstart.md](./quickstart.md).

**Organization**: por historia de usuario. Cada fase es un PR a `develop` (CONTRIBUTING §2),
con la rama indicada en la fase.

**Tareas manuales:** las marcadas con 👤 las hace un lead (ADR-013): cuentas, secretos,
DNS y SSH. El agente nunca ve valores de secretos.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1–US5 de [spec.md](./spec.md)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: estructura de carpetas y herramientas de prueba. Rama `backend/t-i08-deploy-scripts`.

- [ ] T001 Crear `infrastructure/deploy/{remote,tests}/`, `infrastructure/vps/`, `infrastructure/observability/dashboards/` y `docs/runbooks/` con un `README.md` mínimo en `infrastructure/deploy/README.md` que describa el flujo de [plan.md](./plan.md)
- [ ] T002 [P] Agregar `.shellcheckrc` en la raíz del repo (`shell=bash`, `external-sources=true`) y hacer que todo script nuevo empiece con `#!/usr/bin/env bash` y `set -euo pipefail`
- [ ] T003 [P] Borrar `infrastructure/scripts/deploy.sh` (despliegue a Kubernetes, obsoleto por ADR-011) y su mención en `infrastructure/scripts/setup.sh` si existe

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: versión por servicio y scripts remotos de los que dependen US1, US2 y US4.
Rama `backend/t-i08-deploy-scripts` (mismo PR que la fase 1).

**⚠️ CRITICAL**: sin esta fase no hay despliegue ni rollback.

- [ ] T004 En `docker-compose.prod.yml`, reemplazar `${IMAGE_TAG:?}` por una variable por servicio: `TAG_API_GATEWAY`, `TAG_AUTH_SERVICE`, `TAG_SOCIAL_SERVICE`, `TAG_EVENT_SERVICE`, `TAG_CHECKIN_SERVICE`, `TAG_MOSAIC_SERVICE`, `TAG_STREAMING_SERVICE`, `TAG_ARCHIVE_SERVICE`, `TAG_CHAT_SERVICE`, `TAG_MEDIA_SERVICE`, `TAG_TRANSCODE_SERVICE`, `TAG_MODERATION_SERVICE`, `TAG_MONGO_BACKUP`, todas con `:?` (data-model: "siempre las 13 (no puede faltar ninguna: el compose usa `:?`)"). En `caddy` y `srs`, agregar `labels: { vito.config-hash: "${CFG_CADDY:?}" }` y `"${CFG_SRS:?}"` (R4). Actualizar el encabezado del archivo (R1)
- [ ] T005 [P] En `.env.prod.example`, quitar `IMAGE_TAG`, documentar que las versiones llegan por `versions.env` (R1) y que el `.env` completo es el secreto `DOTENV_PRODUCTION` (R15)
- [ ] T006 [P] Crear `infrastructure/deploy/tests/fixtures/versions.env` y `infrastructure/deploy/tests/fixtures/.env.test` (valores ficticios, sin secretos reales) para validar el compose en CI
- [ ] T007 Escribir `infrastructure/deploy/tests/deploy.bats` con un `docker` falso en el `PATH`. Casos: (a) `apply` genera `versions.env` copiando el activo y cambiando solo `--changed`, más `DEPLOYED_SHA`; (b) en el primer despliegue, todas las `TAG_*` valen el SHA; (c) `config -q` fallido → exit 2 sin cambiar `current`; (d) imagen faltante → exit 4; (e) salud fallida con anterior → `current` vuelve, exit 5; (f) sin anterior → exit 6; (g) poda: quedan 3 bundles con éxito; (h) `rollback` a un SHA no retenido → exit 4; (i) cada ejecución agrega una línea JSON válida a `deployments.log` con los campos de data-model (*Despliegue*); (j) `CFG_CADDY` se recalcula y no cambia si el Caddyfile no cambió, y cambia si cambió; (k) toda llamada a `docker compose` lleva `--project-directory $VITO_ROOT/current`; (l) `check` no cambia `current` ni llama a `up`; (m) `versions.env` incluye `RELEASE_VERSION`. Deben fallar antes de T008
- [ ] T008 Implementar `infrastructure/deploy/remote/deploy.sh` (`apply | rollback | status`) según [contracts/remote-scripts.md](./contracts/remote-scripts.md): bundles en `/opt/vito/releases/<sha>/`, symlink `current`, `docker compose --env-file /opt/vito/.env --env-file <bundle>/versions.env config -q`, `up -d --wait --wait-timeout 300`, siempre con `--project-directory /opt/vito/current`, `CFG_*` y `RELEASE_VERSION` en `versions.env`, comando `check` de solo lectura, vuelta automática, `deployments.log` y poda (R4). `/opt/vito` configurable por `VITO_ROOT` para los tests. Hasta que pasen los tests de T007
- [ ] T009 [P] Escribir `infrastructure/deploy/tests/changed-services.bats`: tabla de R2 fila por fila; sin SHA previo → todos; SHA previo que no es ancestro → todos; solo `docs/**` → vacío; `docker-compose.prod.yml` → vacío más `bundle_changed=true`
- [ ] T010 Implementar `infrastructure/deploy/changed-services.sh <prev_sha|-> <sha>`, que imprime `services=<lista>` y `bundle_changed=<bool>` para `$GITHUB_OUTPUT` (R2). Hasta que pase T009
- [ ] T011 Agregar a `.github/workflows/ci.yml` el job `deploy-scripts` (nombre `Deploy Scripts`): `shellcheck` sobre `infrastructure/**/*.sh`, `bats infrastructure/deploy/tests`, `docker compose -f docker-compose.prod.yml --env-file infrastructure/deploy/tests/fixtures/.env.test --env-file infrastructure/deploy/tests/fixtures/versions.env config -q`
- [ ] T012 En `.github/workflows/ci.yml`, sumar `moderation-service` (`ai-services/moderation-service`) y `mongo-backup` (`infrastructure/backup`) a la matriz de `docker-build` (R14)
- [ ] T013 Actualizar los checks obligatorios en `CONTRIBUTING.md` §1 con `Deploy Scripts`, `Secrets Scan` (T048), `Version Check` (T052) y los dos `Docker Build (…)` nuevos. 👤 Un lead los agrega a la protección de `main` y `develop` al mergear (CONTRIBUTING §1)

**Checkpoint**: `Deploy Scripts` verde y compose validado con versión por servicio.

---

## Phase 3: User Story 1 - Un release aprobado llega a producción sin pasos manuales (Priority: P1) 🎯 MVP

**Goal**: merge a `main` → construir lo cambiado → aprobación → instalar → salud → vuelta
automática si falla (FR-001 a FR-012, FR-028).

**Independent Test**: [quickstart.md](./quickstart.md) pasos 1 a 4.

Rama `backend/t-i08-cd-pipeline`.

### Tests for User Story 1

- [ ] T014 [P] [US1] Escribir `infrastructure/deploy/tests/live-check.bats` con un `mongosh` falso: sin eventos → exit 0; evento `PUBLISHED` con `details.startDate ≤ ahora + 1 h` y `details.endDate ≥ ahora − 1 h` → exit 3 y JSON `{"events":[{"id","title"}],"streams":[]}`; stream con `status ∈ {LIVE, CONNECTING, BUFFERING}` → exit 3; `mongosh` falla → exit 7; sin `$VITO_ROOT/current` → exit 0 con `{"skipped":"first-deploy"}` y sin llamar a `mongosh`; contenedor `mongodb` detenido → mismo resultado (R6)

### Implementation for User Story 1

- [ ] T015 [P] [US1] Implementar `infrastructure/deploy/remote/ensure-ro-user.sh`: crea o actualiza `vito_deploy_ro` con rol `read` en `vito_events` y `vito_streaming`, usando las credenciales root del `.env` y la contraseña de `MONGO_DEPLOY_RO_PASSWORD`; idempotente y sin imprimir secretos
- [ ] T016 [US1] Implementar `infrastructure/deploy/remote/live-check.sh` según [contracts/remote-scripts.md](./contracts/remote-scripts.md), con la contraseña solo por variable de entorno. Hasta que pase T014
- [ ] T017 [US1] Reescribir `.github/workflows/cd.yml`, job `changes`: `on: push: [main]` y `workflow_dispatch` (`mode: deploy|preflight`, `force_live_event`, `reason`); obtiene el último SHA con despliegue exitoso del Environment `production` o `production-hotfix` (Deployments API); llama a `changed-services.sh`; llama a `infrastructure/deploy/pick-environment.sh`, que obtiene el PR del commit (`GET /commits/{sha}/pulls`) y fija `kind` (`hotfix` si la rama de origen empieza con `hotfix/`) y el Environment (R5): `production-preflight` si `mode=preflight`; lee `VERSION` (R1). Sale sin más jobs si no hay servicios ni cambios de bundle
- [ ] T018 [US1] En `.github/workflows/cd.yml`, job `build` (sin secretos, `permissions: contents: read`): matriz con `services`, Buildx con `cache-from/to: type=gha,scope=<svc>` (los mismos scopes que `docker-build` de CI), tags `vito/<svc>:<sha>` y `vito/<svc>:v<VERSION>` con la etiqueta OCI `org.opencontainers.image.version` (R1), escaneo Trivy de T049 antes de exportar, `docker save | zstd -T0 -19` y `upload-artifact` con `retention-days: 1` (R3)
- [ ] T019 [US1] En `.github/workflows/cd.yml`, job `deploy` con `environment: ${{ needs.changes.outputs.environment }}` y `concurrency: { group: ${{ inputs.mode == 'preflight' && 'vps-preflight' || 'vps-production' }}, cancel-in-progress: false }` (R8), en este orden:
  1. verificar la autoaprobación (`GET /actions/runs/{id}/approvals`): si en `production-hotfix` aprobó quien disparó el run y el comentario está vacío, falla (FR-002);
  2. escribir `SSH_KEY` (`0600`) y `SSH_KNOWN_HOSTS`, con `StrictHostKeyChecking=yes` (FR-014) y `::add-mask::` para el host;
  3. escribir `DOTENV_PRODUCTION` en `/opt/vito/.env` con `0600` (R15);
  4. subir el bundle a `/opt/vito/releases/<sha>/`;
  5. `ensure-ro-user.sh` (se omite si `mongodb` no corre) y `live-check.sh`: con exit 3 o 7, fallar y avisar, salvo `force_live_event` con `reason` en hotfix (FR-028); un release normal con `force_live_event` falla;
  6. con `mode=preflight`, solo `deploy.sh check <sha>` y salir;
  7. cargar las imágenes (`zstd -d | ssh docker load`);
  8. `deploy.sh apply <sha> --changed … --meta <json>`;
  9. con exit 0, crear el tag git `v<VERSION>` si no existe (`permissions: contents: write` solo en este paso);
  10. escribir el resumen del despliegue en `$GITHUB_STEP_SUMMARY`;
  11. borrar la llave en `always()`.
- [ ] T020 [US1] Agregar a `.github/workflows/cd.yml` un paso `notify` en `failure()` y en los exit 3, 5, 6 y 7: mensaje a Telegram (Bot API, `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`) y correo con Resend a `ALERT_EMAILS` si existe `RESEND_API_KEY` (R11). Incluye resultado, SHA, servicios y enlace al run, sin secretos
- [ ] T021 [US1] Escribir `docs/runbooks/despliegue.md`: cómo leer el resumen y `deploy.sh status`; qué hacer con cada código de salida (2, 4, 5, 6); hotfix con autoaprobación (comentario obligatorio); forzar durante un evento en vivo; primer despliegue

**Checkpoint**: con el aprovisionamiento hecho (US5), un merge a `main` despliega solo y
vuelve atrás solo si algo falla.

---

## Phase 4: User Story 5 - Aprovisionamiento completo y documentado (Priority: P1)

**Goal**: servidor preparado y todo lo necesario cargado en GitHub, Cloudflare, Resend y
Telegram (FR-013 a FR-017). Es el requisito para validar US1 en el servidor real.

**Independent Test**: [quickstart.md](./quickstart.md) paso 0 (`mode=preflight`).

Rama `backend/t-i06-bootstrap` (en paralelo con la fase 3).

### Tests for User Story 5

- [ ] T022 [P] [US5] Agregar a `Deploy Scripts` la ejecución de `infrastructure/vps/bootstrap.sh --check-only` en un contenedor `ubuntu:24.04`: debe salir distinto de 0 (Docker no instalado) sin modificar nada, y `shellcheck` sin advertencias

### Implementation for User Story 5

- [ ] T023 [US5] Implementar `infrastructure/vps/bootstrap.sh` (R7, R16). Aborta si el sistema no es Ubuntu 22.04 o 24.04. Instala Docker Engine y el plugin Compose (repositorio oficial), `zstd`, `ufw`, `fail2ban` y `unattended-upgrades`. Crea el usuario `deploy` en el grupo `docker`, con `authorized_keys` desde cada `--pubkey`. SSH: `PasswordAuthentication no`, `PermitRootLogin prohibit-password`. `ufw`: `limit 22/tcp`, `allow 80,443,1935/tcp`, `allow 8000/udp`, el resto denegado. Crea `/opt/vito/{releases}` y `.env` con `0600`, dueño `deploy`. Es idempotente y tiene `--check-only`, que imprime el chequeo final
- [ ] T024 [US5] Escribir `docs/runbooks/aprovisionamiento.md` (FR-016), sin valores reales:
  1. generar la llave ed25519 del pipeline y obtener la huella con `ssh-keyscan`, verificándola a mano contra la consola de Contabo;
  2. correr `bootstrap.sh`;
  3. crear los Environments `production` y `production-hotfix` con la tabla de [workflows.md](./contracts/workflows.md#environments);
  4. cargar los secretos y variables de [workflows.md](./contracts/workflows.md#secretos-y-variables-por-environment-los-dos-environments-llevan-los-mismos);
  5. configurar el DNS en Cloudflare (`api.`, `live.` y `media.` → IP del VPS, con el proxy de Cloudflare apagado en `live.` para RTMP/WebRTC);
  6. crear el proyecto de Pages y su dominio;
  7. configurar Resend (`send.<dominio>`, SPF, DKIM, MX y `_dmarc` con `p=none`; R12);
  8. crear el bot y el grupo de Telegram (obtener `chat_id`);
  9. activar Secret scanning y Push protection (R13);
  10. completar la tabla de ADR-013.
- [ ] T025 [US5] 👤 Ejecutar la guía de T024 en la cuenta real: llave SSH, `bootstrap.sh`, Environments, secretos, DNS, Pages, Resend, Telegram y secret scanning. Registrar el avance en `docs/roadmap/tasks.md` (T-I06) y en ADR-013 (*Actualizaciones*), sin datos sensibles
- [ ] T026 [US5] Ejecutar `cd.yml` con `mode=preflight` (Environment `production-preflight`, sin aprobación) desde `main` (después del primer merge del pipeline) y anotar el resultado en el PR de cierre. Queda bloqueada por T025

**Checkpoint**: preflight verde. US1 se puede validar en el servidor real.

---

## Phase 5: User Story 2 - Volver a una versión anterior (Priority: P2)

**Goal**: rollback a cualquiera de los últimos 3 releases, sin reconstruir (FR-008).

**Independent Test**: [quickstart.md](./quickstart.md) paso 5.

Rama `backend/t-i08-cd-pipeline` (mismo PR que la fase 3).

### Tests for User Story 2

- [ ] T027 [P] [US2] Extender `infrastructure/deploy/tests/deploy.bats`: `rollback` a un SHA retenido activa su bundle sin llamar a `docker load` ni a `build`, y registra `type: rollback` con `from_sha`; un rollback cuya salud falla vuelve al bundle que estaba (exit 5)

### Implementation for User Story 2

- [ ] T028 [US2] Crear `.github/workflows/rollback.yml`: `workflow_dispatch` con `sha` y `reason` obligatorios y `force_live_event`; Environment `production`; `concurrency: vps-production`. Pasos de SSH y `.env` iguales a T019 (extraer a una acción compuesta `.github/actions/vps-ssh/action.yml` y usarla en los dos workflows); `live-check` (bloquea salvo `force_live_event` con `reason`, FR-028); `deploy.sh rollback <sha> --meta …`; resumen y `notify` como T020
- [ ] T029 [US2] Agregar a `docs/runbooks/despliegue.md` la sección de rollback: cómo elegir el SHA (`deploy.sh status`, historial de Deployments), qué pasa con la base de datos (no se revierte) y cuándo hace falta un hotfix en vez de un rollback

**Checkpoint**: rollback probado con dos releases reales.

---

## Phase 6: User Story 3 - La web se publica desde `main` en el hosting definitivo (Priority: P2)

**Goal**: web en Cloudflare Pages con aprobación, URLs obligatorias y rutas internas
funcionando (FR-018 a FR-022).

**Independent Test**: [quickstart.md](./quickstart.md) paso 6.

Rama `frontend/t-i11-pages` (independiente de las fases 3 a 5).

### Tests for User Story 3

- [ ] T030 [P] [US3] Agregar a `flutter-app` en `.github/workflows/ci.yml` un paso que, tras `flutter build web`, verifique que `build/web/_redirects` y `build/web/_headers` existen y que `_redirects` contiene `/* /index.html 200`

### Implementation for User Story 3

- [ ] T031 [P] [US3] Crear `frontend/vito_app/web/_redirects` (`/* /index.html 200`) y `frontend/vito_app/web/_headers` (`X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, `X-Frame-Options: DENY`, `Strict-Transport-Security: max-age=31536000; includeSubDomains`, la CSP de T-I18 con los orígenes completados por `deploy-frontend.yml` desde `vars.*`, `Cache-Control: public, max-age=31536000, immutable` en `/assets/*`) y `flutter build web --no-web-resources-cdn` (R9, T-I18)
- [ ] T032 [US3] Reescribir `.github/workflows/deploy-frontend.yml`: nombre `Deploy Frontend (Cloudflare Pages)`; `environment` según `infrastructure/deploy/pick-environment.sh` (`production` o `production-hotfix`, FR-021); `concurrency: pages-production`; puertas `analyze` y `test` iguales; el paso de build falla si `vars.VITO_API_BASE_URL` o `vars.VITO_WS_BASE_URL` están vacías (FR-019) y siempre pasa los dos `--dart-define`; despliega con `npx wrangler@<versión fija> pages deploy build/web --project-name=${{ vars.PAGES_PROJECT }} --branch=main` usando `CLOUDFLARE_API_TOKEN` y `CLOUDFLARE_ACCOUNT_ID`
- [ ] T033 [US3] Después de verificar la primera publicación en Pages (quickstart paso 6): borrar `frontend/vito_app/vercel.json` y cualquier referencia a Vercel en `.github/workflows/` y `docs/`. 👤 Un lead borra los secretos `VERCEL_*` y el proyecto de Vercel (FR-022)

**Checkpoint**: la web de producción sale de Pages; Vercel retirado.

---

## Phase 7: User Story 4 - Los leads se enteran de una caída antes que los usuarios (Priority: P3)

**Goal**: métricas, logs y trazas en Grafana Cloud, tablero RED y alertas por correo y
Telegram (FR-023 a FR-027).

**Independent Test**: [quickstart.md](./quickstart.md) paso 7.

Rama `backend/t-i13-observability` (depende de la fase 2 por el compose).

### Tests for User Story 4

- [ ] T034 [P] [US4] Agregar a `Deploy Scripts` la validación de `infrastructure/observability/config.alloy` con la imagen fijada de Alloy (`alloy fmt` sin cambios y `alloy validate`), más un test que pase líneas de ejemplo con `Bearer abc`, `?token=abc`, `"password":"x"`, un JWT `eyJhbGciOi.eyJzdWIi.c2ln` y un correo por la etapa de enmascarado y verifique que no queda ninguno (FR-026)

### Implementation for User Story 4

- [ ] T035 [US4] Escribir `infrastructure/observability/config.alloy` según [contracts/observability.md](./contracts/observability.md):
  - extracción de `/actuator/prometheus` de los 10 Java (gateway en `:9080`), con la lista blanca de métricas;
  - `prometheus.exporter.unix` y `prometheus.exporter.cadvisor`;
  - `loki.source.docker` contra `http://docker-socket-proxy:2375`, con etiqueta `service`, `level` extraído del JSON con `stage.json` (T053) y la etapa de enmascarado (incluye JWT);
  - receptor OTLP;
  - exportación a Grafana Cloud con `GRAFANA_CLOUD_*` del entorno.
- [ ] T036 [US4] En `docker-compose.prod.yml`, agregar `docker-socket-proxy` (imagen fijada, `CONTAINERS=1`, `INFO=1`, el resto en 0, socket montado solo en este contenedor, sin puertos, `mem_limit: 32m`) y `alloy` (imagen fijada, `mem_limit: 256m`, `labels: { vito.config-hash: "${CFG_ALLOY:?}" }`, monta `config.alloy` en solo lectura, `/proc`, `/sys` y `/` en solo lectura para el exportador del host, sin socket de Docker). Actualizar la tabla de memoria del encabezado (~9.630 MiB). Agregar `GRAFANA_CLOUD_*` a `.env.prod.example`
- [ ] T037 [P] [US4] En `infrastructure/caddy/Caddyfile`, agregar en `api.{$DOMAIN}` un `handle /healthz` que reescriba a `/actuator/health/readiness` y haga proxy a `api-gateway:9080`, devolviendo solo el estado. Documentarlo como ruta de infraestructura en `docs/api/CONVENTIONS.md` (*Complexity Tracking* del plan)
- [ ] T038 [P] [US4] Agregar tracing OTel a los 10 servicios Java: `micrometer-tracing-bridge-otel` y `opentelemetry-exporter-otlp` en cada `backend/<svc>/pom.xml` (o en `backend/shared-web` si T-B18 ya existe); `management.tracing.sampling.probability: 0.1` y `management.otlp.tracing.endpoint: http://alloy:4318/v1/traces` en el perfil `prod` de cada `application.yml`. Test de contexto por servicio que arranque con la configuración de tracing
- [ ] T039 [P] [US4] Crear `infrastructure/observability/dashboards/vito-api-red.json` con los paneles de [contracts/observability.md](./contracts/observability.md#tablero-vito-api-red-fr-024)
- [ ] T040 [US4] Escribir `infrastructure/observability/README.md` con los pasos para crear en Grafana Cloud el contact point `leads` (correo + Telegram), las cuatro reglas de [contracts/observability.md](./contracts/observability.md#alertas-fr-025) (`api-down`, `service-unhealthy`, `host-disk-high`/`host-mem-high`, `backup-missing`), el check de Synthetic Monitoring contra `/healthz` y la importación del tablero
- [ ] T041 [US4] 👤 Crear en Grafana Cloud el contact point, las alertas, el check sintético y el tablero según T040; probar `api-down` deteniendo `api-gateway` (quickstart paso 7)

**Checkpoint**: alerta de prueba recibida en Telegram y en el correo; tablero con datos reales.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: primer release, validación completa y cierre del backlog. Rama `docs/close-cd`.

- [ ] T042 👤 Abrir el PR de release extraordinario `develop` → `main` (aclaración 4), con `VERSION` en `0.1.0`. Bloqueada por T051; en ADR-012 (*Actualizaciones*), registrar la excepción a la cadencia de los viernes
- [ ] T043 Ejecutar [quickstart.md](./quickstart.md) completo (pasos 0 a 8) en producción y pegar los resultados en el PR de cierre (sin IP ni secretos). A los 7 días, revisar en Grafana Cloud el uso de series, logs y trazas frente al plan gratuito (FR-027)
- [ ] T044 [P] Revisar los logs de todos los runs de T043 buscando IP, dominios privados y valores del `.env`; confirmar que Secret scanning no reporta nada (SC-007)
- [ ] T045 [P] Actualizar `docs/roadmap/tasks.md`: T-I06, T-I08, T-I11 y T-I13 → ✅ HECHA con sus PR; T-I12 sin cambios; quitar T-I08/T-I11/T-I13 de "Bloqueadas". Actualizar la Etapa 5 en `docs/roadmap/README.md`. Anotar el costo del mes en la hoja de gastos de ADR-013 (SC-008)
- [ ] T046 [P] Agregar a la constitución (PATCH) el cierre de O-12 en la tabla de observaciones, con el Sync Impact Report, en una rama `docs/constitution-close-o12`
- [ ] T047 [P] Proponer en `docs/roadmap/tasks.md` la tarea futura `/internal/live-status` (deuda del Principio II del plan) para la Ola 4

### Agregadas tras `/speckit-analyze` (2026-10-22)

Se numeran al final para no renumerar las anteriores. La columna "Fase" dice en qué PR
van y antes de qué tarea.

- [ ] T048 [P] (Fase B, con T011) Agregar a `.github/workflows/ci.yml` el job `secrets-scan` (nombre `Secrets Scan`): `docker run` de `zricethezav/gitleaks:<versión fija>` con `detect --source . --log-opts="<base>..<head>" --redact --exit-code 1`, checkout con `fetch-depth: 0`, sin secretos; `.gitleaks.toml` solo si hace falta ignorar fixtures ficticios (R13)
- [ ] T049 (Fase C, con T018) En el job `build` de `.github/workflows/cd.yml`, escanear cada imagen con `aquasecurity/trivy-action@<SHA fijo>` (`severity: CRITICAL,HIGH`, `exit-code: 1`, `ignore-unfixed: true`, `trivyignores: .trivyignore`) antes de `docker save`. Crear `.trivyignore` vacío con el formato de excepción comentado (motivo, aprobó, vence). En la matriz `docker-build` de `ci.yml`, el mismo escaneo con `exit-code: 0` y subida del SARIF con `github/codeql-action/upload-sarif` (`permissions: security-events: write`) (R17)
- [ ] T050 [P] (Fase B) Crear `.github/dependabot.yml`: `maven` en cada `backend/<svc>` (10), `pip` en `ai-services/transcode-service` y `ai-services/moderation-service`, `pub` en `frontend/vito_app`, `github-actions` en `/` y `docker` en `backend/` y `ai-services/*`; `interval: weekly`, `target-branch: develop`, `open-pull-requests-limit: 5` (R18). Sumar a la guía de T024 la activación de las alertas de Dependabot
- [ ] T051 👤 (antes de T042) Triage de `backend/SECURITY_CHECKLIST.md`: los leads asignan severidad (alta/media/baja) a cada uno de los 38 `TODO`. Cada `TODO` de severidad alta se resuelve con una tarea en `docs/roadmap/tasks.md` o queda como excepción documentada en el mismo archivo (motivo, responsable, fecha de revisión), según el Principio III. El agente prepara la propuesta de severidades; los leads la aprueban
- [ ] T052 [P] (Fase B) Crear `VERSION` en la raíz con `0.1.0` y el job `version-check` (nombre `Version Check`) en `ci.yml`: en PR con base `main`, falla si `VERSION` no es semver o no es mayor que el último tag `v*`; en otros PR, se salta (R1)
- [ ] T053 [P] [US4] (Fase E, con T038) Logs JSON en todos los perfiles (Principio VI): `net.logstash.logback:logstash-logback-encoder` en cada `backend/<svc>/pom.xml` (o en `backend/shared-web` si T-B18 ya existe) y `src/main/resources/logback-spring.xml` con `LogstashEncoder`, `customFields` `{"service":"${spring.application.name}"}` y los campos del MDC `traceId`, `spanId` y `userId`; en `ai-services/*`, `python-json-logger` con los mismos campos. Test por servicio que verifique que una línea de log es JSON válido con `service`
- [ ] T054 (Fase C, con T028) Crear `.github/workflows/expire-approvals.yml` según [contracts/workflows.md](./contracts/workflows.md#expire-approvalsyml--plazo-de-24-h-fr-002): `schedule` cada hora y `workflow_dispatch`, `permissions: actions: write`; cancela los runs en `waiting` con más de 24 h de `cd.yml`, `rollback.yml` y `deploy-frontend.yml` y avisa como T020 (FR-002)
- [ ] T055 [P] (Fase C, con T017) Implementar `infrastructure/deploy/pick-environment.sh <sha> <mode>`, que imprime `kind=` y `environment=` para `$GITHUB_OUTPUT`, con su test `infrastructure/deploy/tests/pick-environment.bats` (PR desde `develop` → `production`; desde `hotfix/*` → `production-hotfix`; `mode=preflight` → `production-preflight`; sin PR → `production`) (R5, R8)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (1)** y **Foundational (2)**: mismo PR; bloquean US1, US2 y US4.
- **US1 (3)**: depende de 2. Se puede mergear sin US5, pero su validación real (T026, T043) necesita US5.
- **US5 (4)**: independiente del código. T025 es manual y en paralelo con todo; T026 necesita US1 en `main`.
- **US2 (5)**: depende de US1 (comparte la acción `vps-ssh` y `deploy.sh`).
- **US3 (6)**: independiente; solo necesita T025 (token y proyecto de Pages) para publicar.
- **US4 (7)**: depende de 2 (compose). T041 necesita el primer despliegue.
- **Polish (8)**: depende de todo lo anterior. T042 además espera a T051.
- **T048–T055**: van en el PR de la fase indicada en cada tarea.

### User Story Dependencies

```
Fase 2 ──► US1 ──► US2
   │        │
   │        └──(validación)──◄── US5 (manual, paralelo)
   └──► US4                       │
US3 ◄────────────(token Pages)────┘
```

### Within Each User Story

- Los tests `bats` se escriben antes y fallan antes de implementar.
- Scripts remotos antes que los workflows que los llaman.
- Workflows antes que los runbooks que describen su operación.

### Parallel Opportunities

- T002 y T003; T005, T006 y T009 (después de T004 y T001).
- Fases 3, 4 y 6 en paralelo con personas distintas: backend (US1), un lead (US5), frontend (US3).
- En US4: T037, T038 y T039 en paralelo, después de T035 y T036.

---

## Parallel Example: User Story 1

```bash
# Después de la fase 2, en paralelo:
Task: "T014 tests de live-check en infrastructure/deploy/tests/live-check.bats"
Task: "T015 ensure-ro-user.sh en infrastructure/deploy/remote/ensure-ro-user.sh"
# Luego, en secuencia (mismo archivo .github/workflows/cd.yml): T017 → T018 → T019 → T020
```

## Parallel Example: User Story 4

```bash
Task: "T037 /healthz en infrastructure/caddy/Caddyfile"
Task: "T038 tracing OTel en backend/*/pom.xml y application.yml"
Task: "T039 tablero en infrastructure/observability/dashboards/vito-api-red.json"
```

---

## Implementation Strategy

### MVP First

1. Fases 1 y 2 (scripts y compose, con tests).
2. Fase 3 (US1), mientras un lead hace T025 (US5).
3. **STOP and VALIDATE:** preflight (T026) y primer release extraordinario (T042). Producción viva.

### Incremental Delivery

1. MVP → backend en producción con aprobación y vuelta automática.
2. US2 → rollback manual.
3. US3 → web en Pages; se retira Vercel.
4. US4 → alertas y tablero. **No se abre a usuarios reales sin US4** (spec, Historia 4).
5. Polish → quickstart completo y cierre de T-I06/08/11/13.

### Mapeo a `docs/roadmap/tasks.md`

| Backlog | Tareas de esta feature |
|---------|------------------------|
| T-I06 | T022–T026, T051 |
| T-I08 | T001–T021, T027–T029, T048–T050, T052, T054, T055 |
| T-I11 | T030–T033 |
| T-I13 | T034–T041, T053 |

---

## Notes

- 👤 = tarea manual de un lead. El agente prepara la guía y verifica el resultado, pero no
  ejecuta ni ve secretos.
- Cada fase es un PR a `develop`, sin apilar (CONTRIBUTING §2).
- Cualquier cambio en el nombre de un job de CI actualiza los checks obligatorios en el
  mismo PR (CONTRIBUTING §1).
