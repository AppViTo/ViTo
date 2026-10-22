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
| `mode` | `deploy` · `preflight`, default `deploy` | `preflight` verifica SSH, huella, secretos y `config -q` sin reiniciar nada (Historia 5) |
| `force_live_event` | boolean, default `false` | solo válido si el commit es hotfix (Environment `production-hotfix`) |
| `reason` | string | obligatorio si `force_live_event` |

Jobs, en orden:

| Job | Environment | Secretos | Hace |
|-----|-------------|:--------:|------|
| `changes` | — | no | último SHA desplegado (Deployments API), servicios cambiados (R2), `kind` y Environment (R5) |
| `build` | — | no | construye y exporta las imágenes cambiadas como artifacts (R3); se omite si no hay |
| `deploy` | `production` o `production-hotfix` | sí | verifica la autoaprobación (R5), conecta por SSH con huella fija, `live-check`, sube el bundle y las imágenes, ejecuta `deploy.sh apply`, publica el resumen y avisa |

`concurrency: vps-production` (sin cancelar el que está en curso).

## `rollback.yml` — volver a un release anterior

| Input | Tipo | Regla |
|-------|------|-------|
| `sha` | string, requerido | uno de los últimos 3 releases desplegados con éxito; si no, falla antes de tocar servicios |
| `reason` | string, requerido | queda en el registro |
| `force_live_event` | boolean, default `false` | permite rollback durante un evento en vivo (R6) |

Environment `production` (aprueba el otro lead). Mismo grupo de concurrencia que `cd.yml`.
No construye imágenes.

## `deploy-frontend.yml` — web en Cloudflare Pages

| Disparador | `push` a `main` con cambios en `frontend/vito_app/**` o en el workflow; `workflow_dispatch` |
|---|---|
| Environment | `production` |
| Puertas | `flutter analyze --fatal-infos`, `flutter test`, URLs de producción no vacías |
| Concurrencia | `pages-production` |

## Environments

| Environment | Revisores obligatorios | Prevent self-review | Ramas permitidas |
|-------------|------------------------|:-------------------:|------------------|
| `production` | los dos leads | ✅ | `main` |
| `production-hotfix` | los dos leads | ❌ | `main` |

## Secretos y variables (por Environment; los dos Environments llevan los mismos)

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

El dominio no se escribe en el repo: llega por `DOTENV_PRODUCTION` (`DOMAIN`) y por estas
variables.
