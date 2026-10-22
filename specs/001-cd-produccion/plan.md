# Implementation Plan: Despliegue continuo a producción (Fase Tracción)

**Branch**: `docs/001-cd-produccion` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-cd-produccion/spec.md`

## Summary

Llevar `main` a producción en el VPS de ADR-011 sin pasos manuales, con doble aprobación
(PR de release + Environment protegido), una versión por servicio, vuelta automática si un
servicio no queda sano, rollback a los últimos 3 releases y bloqueo durante eventos en vivo.
Publicar la web en Cloudflare Pages y enviar métricas, logs y trazas a Grafana Cloud, con
alertas por correo y Telegram.

Enfoque técnico ([research.md](./research.md)):
- El compose pasa de un `IMAGE_TAG` único a una variable `TAG_*` por servicio, con un
  manifiesto `versions.env` por despliegue (R1).
- El CI construye solo lo que cambió desde el último despliegue exitoso (Deployments API) y
  lleva las imágenes con `docker save | ssh docker load` (R2, R3).
- Un bundle por release en el VPS, con un symlink `current`, y `docker compose up -d --wait`
  con vuelta automática (R4).
- Dos Environments: `production` con *prevent self-review* y `production-hotfix` con
  autoaprobación justificada (R5).
- Comprobación de eventos en vivo con un usuario Mongo de solo lectura (R6).
- Grafana Alloy en el VPS (R10).

## Technical Context

**Language/Version**: Bash (scripts del VPS y del pipeline), YAML de GitHub Actions y de
Docker Compose v2, Alloy config (River). Java 17 / Spring Boot solo para agregar tracing
OTel a los servicios. Flutter 3.47 sin cambios de código (solo `web/_redirects` y
`web/_headers`).

**Primary Dependencies**: GitHub Actions (`actions/checkout`, `docker/setup-buildx-action`,
`docker/build-push-action`, `actions/upload-artifact`/`download-artifact`), Docker Engine +
Compose v2 en Ubuntu, `zstd`, `ufw`, `fail2ban`, `wrangler` (Pages, versión fijada),
Grafana Alloy, `tecnativa/docker-socket-proxy`, `micrometer-tracing-bridge-otel` +
`opentelemetry-exporter-otlp`, Bot API de Telegram, API de Resend.

**Storage**: archivos en el VPS (`/opt/vito/releases/<sha>/`, `current`, `.env`,
`deployments.log`); Deployments API de GitHub como registro del último SHA exitoso. Mongo:
solo un usuario nuevo de solo lectura.

**Testing**: `shellcheck` sobre todos los scripts; `bats` para `changed-services.sh` y la
generación y rotación del manifiesto en `deploy.sh` (con un `docker` falso);
`docker compose -f docker-compose.prod.yml config -q` con un `.env` de prueba en CI;
`alloy fmt`/`alloy validate`. La validación de punta a punta está en
[quickstart.md](./quickstart.md).

**Target Platform**: VPS Contabo 12 GB / 6 vCPU, EE. UU., Ubuntu 24.04 LTS (supuesto; el
bootstrap valida la versión). Runners `ubuntu-latest` de GitHub. Cloudflare Pages.

**Project Type**: infraestructura y entrega (pipeline + configuración del servidor) de un
monorepo de microservicios.

**Performance Goals**: aprobación → producción sana ≤ 15 min con 1 servicio (SC-001);
rollback ≤ 5 min (SC-003); vuelta automática ≤ 10 min (SC-004); alerta de caída ≤ 5 min
(SC-005).

**Constraints**: presupuesto de ADR-011 (sin servicios pagos nuevos); 12 GB de RAM (Alloy
256m + proxy 32m, total ~9,6 GiB); plan gratuito de Grafana Cloud (10k series, 50 GB de
logs y de trazas, 14 días); repo público (nada sensible en el repo ni en los logs);
protección de rama con checks obligatorios por nombre (CONTRIBUTING §1).

**Scale/Scope**: 13 servicios desplegables + 7 de terceros en un solo host; ~1 release por
semana más hotfixes; 2 leads.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.* Constitución
**v1.3.0** (PR #61; este plan se mergea después).

| Principio | Evaluación | Estado |
|-----------|-----------|:------:|
| I. Contrato primero | Ningún endpoint de negocio nuevo. `GET /healthz` lo responde Caddy en `api.<dominio>` (proxy al readiness del gateway) y es operacional, no lo consume el frontend. → *Complexity Tracking*. | ⚠️ justificado |
| II. Hexagonal y autonomía | Sin cambios de capas. `live-check.sh` lee `vito_events` y `vito_streaming` con un usuario de solo lectura desde operación, no desde un servicio. → *Complexity Tracking*. El tracing OTel es configuración de infraestructura de cada servicio. | ⚠️ justificado |
| III. Seguridad por defecto | Secretos solo en Environments/gestor/`.env 0600`; llave SSH dedicada, huella fija, sin contraseñas; datastores sin puertos (sin cambios); máscaras en logs; Alloy no monta el socket de Docker: pasa por `docker-socket-proxy` con solo `GET /containers` e `/info` (el montaje `:ro` del socket no limita la API); secret scanning y push protection. El 22 no se puede filtrar por IP (R7): `ufw limit` + `fail2ban` + solo llave. | ✅ (desviación de T-I06 documentada en R7) |
| IV. Calidad verificable | `shellcheck` + `bats` + `compose config` + `alloy validate` en un job nuevo de CI (`Deploy Scripts`), que se suma a los checks obligatorios en el mismo PR (CONTRIBUTING §1). Quickstart cubre todas las historias. | ✅ |
| V. Mensajería | Sin cambios. Kafka es un servicio con estado: no se reinicia salvo cambio de definición (FR-011). | ✅ |
| VI. Observabilidad | Métricas, logs JSON y trazas OTel a Grafana Cloud, health checks ya existentes (T-B14), tablero RED y alertas. | ✅ |
| VII. SLO | Sin efecto en latencias. El bloqueo por evento en vivo protege la disponibilidad durante la carga real. | ✅ |
| VIII. Entrega inmutable | Una imagen por servicio con tag = SHA, sin `:latest`; despliegue desde `main` por SSH; doble aprobación (v1.3.0); rollback = activar un bundle anterior; web solo desde `main`, con URLs obligatorias. El semver de ADR-012 es el tag git del release; `deployments.log` relaciona el SHA con ese tag. | ✅ |
| IX. Datos y resiliencia | Volúmenes de datos nunca se tocan en un despliegue; alerta si falta el backup; logs sin datos personales en claro. | ✅ |
| X. Simplicidad | Solo se agregan Alloy (ya previsto en ADR-011 h) y un proxy de socket de ~10 MB. Sin registro, sin orquestador, sin Terraform. | ✅ |

**Resultado:** pasa con dos desviaciones justificadas. **Re-check post-diseño (fase 1):** sin
cambios; el único ajuste fue el proxy del socket (III), ya reflejado.

## Project Structure

### Documentation (this feature)

```text
specs/001-cd-produccion/
├── spec.md
├── plan.md              # este archivo
├── research.md          # R1–R14
├── data-model.md        # manifiesto, bundle, despliegue, alertas
├── quickstart.md        # validación de punta a punta
├── contracts/
│   ├── workflows.md     # disparadores, inputs, Environments, secretos
│   ├── remote-scripts.md
│   └── observability.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
.github/workflows/
├── cd.yml                     # reescrito: changes → build → deploy (R2–R5)
├── rollback.yml               # nuevo (Historia 2)
├── deploy-frontend.yml        # Vercel → Cloudflare Pages (R9)
└── ci.yml                     # + job "Deploy Scripts"; matriz docker + moderation y mongo-backup (R14)

docker-compose.prod.yml        # TAG_* por servicio; alloy + docker-socket-proxy; healthz en Caddy
.env.prod.example              # + GRAFANA_CLOUD_*, sin IMAGE_TAG

infrastructure/
├── deploy/
│   ├── changed-services.sh    # rutas → servicios (R2), corre en el runner
│   ├── remote/
│   │   ├── deploy.sh          # apply | rollback | status (contrato)
│   │   ├── live-check.sh
│   │   └── ensure-ro-user.sh
│   └── tests/*.bats
├── vps/bootstrap.sh           # preparación idempotente (R7)
├── observability/
│   ├── config.alloy
│   └── dashboards/vito-api-red.json
├── caddy/Caddyfile            # + /healthz
└── scripts/deploy.sh          # se borra (Kubernetes, obsoleto)

frontend/vito_app/
├── web/_redirects, web/_headers   # nuevos (R9)
└── vercel.json                    # se borra tras la primera publicación en Pages (FR-022)

backend/*/pom.xml + application.yml   # tracing OTel con muestreo del 10 % (R10)

docs/runbooks/
├── aprovisionamiento.md       # guía de T-I06 (FR-016): cuentas, DNS, secretos, Resend, Telegram
└── despliegue.md              # operar, rollback, forzar en evento, qué hacer con código 6
```

**Structure Decision**: todo lo de despliegue vive en `infrastructure/deploy/` (lo que va al
VPS en `remote/`) y `infrastructure/vps/`. Así `changed-services.sh` puede tratar
`infrastructure/deploy/remote/**` como parte del bundle y `infrastructure/vps/**` como algo
que no despliega nada.

## Fases de entrega (para `/speckit-tasks`)

| Fase | Contenido | Depende de | Rama |
|------|-----------|------------|------|
| A | Aprovisionamiento: `bootstrap.sh`, `aprovisionamiento.md`, Environments, secretos, DNS, Resend, Telegram, secret scanning | — (los pasos manuales los hacen los leads) | `backend/t-i06-bootstrap` |
| B | Compose con `TAG_*`, scripts remotos con tests, job `Deploy Scripts` en CI | — | `backend/t-i08-deploy-scripts` |
| C | `cd.yml` y `rollback.yml`, verificación previa | B; A para probar | `backend/t-i08-cd-pipeline` |
| D | Web en Pages | A (token, proyecto) | `frontend/t-i11-pages` |
| E | Alloy, `/healthz`, tablero, alertas, tracing OTel | B (compose) | `backend/t-i13-observability` |
| F | Primer release extraordinario, quickstart completo, retirar Vercel, cerrar T-I06/08/11/13 | A–E | release + `docs/close-cd` |

Cada fase es un PR a `develop` (CONTRIBUTING §2, sin apilar).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Principio II: `live-check.sh` lee colecciones de event-service y streaming-service | FR-028 exige saber si hay un evento en curso antes de desplegar, y no existe una API interna para eso | Un endpoint interno exige contrato y una ola (Principio I) solo para operación. Se propone `/internal/live-status` cuando llegue la Ola 4. Mientras tanto: usuario de solo lectura, sin escribir y sin código de servicio. |
| Principio I: `GET /healthz` público en `api.<dominio>` fuera de `openapi.yaml` | El monitor externo de SC-005 necesita una URL pública de salud, y Caddy bloquea `/actuator` | Publicarlo en `openapi.yaml` lo mezclaría con la API de negocio que consume el frontend. Lo sirve el borde, no un servicio, y solo devuelve el estado de readiness, sin detalles. Se registra en `CONVENTIONS.md` como ruta de infraestructura. |
