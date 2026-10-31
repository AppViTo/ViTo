# Contrato: workflows de despliegue, Environments y secretos

Estas son las interfaces que usan los leads: cómo se disparan los workflows, qué reciben y
qué secretos necesitan. No es contrato de API (`docs/api/`): no lo consume el frontend.

## `cd.yml` — despliegue del backend

| Disparador | Condición |
|------------|-----------|
| `push` a `main` | siempre; si `changes` no encuentra servicios ni bundle cambiado, termina sin pedir aprobación |
| `workflow_dispatch` | inputs de abajo; para redesplegar `main` o forzar un hotfix en evento en vivo |

| Input (`workflow_dispatch`) | Tipo | Regla |
|-----------------------------|------|-------|
| `mode` | `deploy` · `preflight`, default `deploy` | `preflight` verifica SSH, huella, secretos y `config -q` sin reiniciar nada (Historia 5); usa el Environment `production-preflight` y el grupo `vps-preflight`, sin aprobación (R8) |
| `force_live_event` | boolean, default `false` | solo válido si el commit es hotfix (Environment `production-hotfix`) |
| `reason` | string | obligatorio si `force_live_event` |
| `rebuild_all` | boolean, default `false` | `true` ignora el último despliegue exitoso y reconstruye todas las imágenes (`changed-services.sh` con SHA previo `-`); para un VPS vacío o reconstruido. El resto del flujo no cambia: Trivy y aprobación del Environment |

Jobs, en orden:

| Job | Environment | Secretos | Hace |
|-----|-------------|:--------:|------|
| `changes` | — | no | último SHA desplegado (Deployments API), servicios cambiados (R2), `kind` y Environment con `pick-environment.sh` (R5), `VERSION` (R1) |
| `build` | — | no | construye las imágenes cambiadas con tag SHA + `v<VERSION>`, las escanea con Trivy (falla con CRITICAL/HIGH fuera de `.trivyignore`, R17) y las exporta como artifacts (R3); se omite si no hay |
| `deploy` | `production`, `production-hotfix` o `production-preflight` | sí | verifica la autoaprobación (R5), conecta por SSH con huella fija, `live-check`, sube el bundle y las imágenes, ejecuta `deploy.sh apply` (o `check` en preflight), crea el tag git `v<VERSION>` si no existe, publica el resumen y avisa |

`concurrency: vps-production` (sin cancelar el que está en curso); `vps-preflight` con `mode=preflight`.

## `rollback.yml` — volver a un release anterior

| Input | Tipo | Regla |
|-------|------|-------|
| `sha` | string, requerido | uno de los últimos 3 releases desplegados con éxito; si no, falla antes de tocar servicios |
| `reason` | string, requerido | queda en el registro |
| `force_live_event` | boolean, default `false` | permite rollback durante un evento en vivo; el motivo es `reason` (R6, FR-028) |

Environment `production-hotfix` (D-13): lo aprueba cualquiera de los dos leads, también quien
lo dispara, con `reason` en el registro. Mismo grupo de concurrencia que `cd.yml`.
No construye imágenes.

## `deploy-frontend.yml` — web en Cloudflare Pages

| Disparador | `push` a `main` con cambios en `frontend/vito_app/**`, en `infrastructure/deploy/render-web-headers.sh` o en el workflow; `workflow_dispatch` |
|---|---|
| Environment | `production` o `production-hotfix`, con la misma detección que `cd.yml` (`pick-environment.sh`) |
| Puertas | `flutter analyze --fatal-infos` y `flutter test` antes de la aprobación; después, `VITO_API_BASE_URL`, `VITO_WS_BASE_URL`, `VITO_MEDIA_ORIGIN` y `PAGES_PROJECT` no vacías |
| Hace | `flutter build web --no-web-resources-cdn`; `render-web-headers.sh` completa los orígenes de la CSP de `web/_headers`; `wrangler pages deploy --branch=main` (versión fija) |
| Concurrencia | `pages-production` |

## `expire-approvals.yml` — plazo de 24 h (FR-002)

| Disparador | `schedule` cada hora; `workflow_dispatch` |
|---|---|
| Permisos | `actions: write`, `contents: read`; sin Environment ni secretos salvo los de aviso |
| Hace | lista los runs en `waiting` de `cd.yml`, `rollback.yml` y `deploy-frontend.yml` con más de 24 h, los cancela (`POST /actions/runs/{id}/cancel`) y avisa por Telegram |

## Environments

| Environment | Revisores obligatorios | Prevent self-review | Ramas permitidas |
|-------------|------------------------|:-------------------:|------------------|
| `production` | los dos leads | ✅ | `main` |
| `production-hotfix` | los dos leads | ❌ | `main` |
| `production-preflight` | ninguno | — | `main` |

## Secretos y variables (por Environment; los tres Environments llevan los mismos)

Ningún valor entra al repo ni al chat. Los carga un lead (T-I06).

**Secretos**

| Nombre | Para qué |
|--------|----------|
| `SSH_HOST` | IP o nombre del VPS |
| `SSH_USER` | `deploy` |
| `SSH_KEY` | llave privada ed25519 del pipeline (solo para el VPS) |
| `SSH_KNOWN_HOSTS` | salida de `ssh-keyscan -t ed25519 <host>` verificada a mano |
| `DOTENV_PRODUCTION` | contenido completo del `.env` de producción (ver `.env.prod.example`); el job lo escribe en `/opt/vito/.env` con `0600` en cada despliegue, así el Environment es la fuente |
| `MONGO_DEPLOY_RO_PASSWORD` | contraseña del usuario de solo lectura `vito_deploy_ro` (R6) |
| `CLOUDFLARE_API_TOKEN` | permiso *Cloudflare Pages: Edit* |
| `CLOUDFLARE_ACCOUNT_ID` | ID de la cuenta |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | avisos del pipeline (R11) |
| `RESEND_API_KEY` | avisos por correo del pipeline y, a futuro, correo de la app |
| `ALERT_EMAILS` | correos de los leads, separados por coma |
| `GRAFANA_CLOUD_*` (URL y usuario de Prometheus, Loki y OTLP, y el token) | van dentro de `DOTENV_PRODUCTION`, los usa Alloy |

**Variables (no secretas)**

| Nombre | Ejemplo |
|--------|---------|
| `VITO_API_BASE_URL` | `https://api.<dominio>/api/v1` |
| `VITO_WS_BASE_URL` | `wss://api.<dominio>` |
| `PAGES_PROJECT` | `vito-web` |
| `VITO_MEDIA_ORIGIN` | `https://media.<dominio>`: origen de las imágenes en la CSP de la web (`img-src` y `connect-src`) |
| `ALERT_FROM` | `ViTo <alertas@send.<dominio>>`: remitente de los avisos por correo (Resend); sin él, solo Telegram |

**Secretos del repositorio** (no de un Environment): `TELEGRAM_BOT_TOKEN` y `TELEGRAM_CHAT_ID`,
para `expire-approvals.yml`, que corre desde la rama por defecto y sin Environment.

El dominio no se escribe en el repo: llega por `DOTENV_PRODUCTION` (`DOMAIN`) y por estas
variables.
