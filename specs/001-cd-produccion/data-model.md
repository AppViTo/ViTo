# Data Model: Despliegue continuo a producción

No hay colecciones nuevas en MongoDB. El "modelo" son archivos de estado en el VPS y
registros de GitHub. Los formatos exactos están en [contracts/](./contracts/).

## Servicio desplegable

Conjunto cerrado: es lo que se puede versionar por separado (R1).

| Servicio | Variable de versión | Origen de la imagen |
|----------|---------------------|---------------------|
| api-gateway, auth-service, social-service, event-service, checkin-service, mosaic-service, streaming-service, archive-service, chat-service, media-service | `TAG_<NOMBRE_EN_MAYÚSCULAS>` (p. ej. `TAG_EVENT_SERVICE`) | `backend/<svc>` + `backend/Dockerfile` |
| transcode-service, moderation-service | `TAG_TRANSCODE_SERVICE`, `TAG_MODERATION_SERVICE` | `ai-services/<svc>` |
| mongo-backup | `TAG_MONGO_BACKUP` | `infrastructure/backup` |

Los servicios de terceros (caddy, mongodb, redis, kafka, srs, alloy) tienen la versión fija
en el compose y cambian solo si cambia el compose (FR-011).

## Release

| Campo | Regla |
|-------|-------|
| `sha` | Commit de `main` (40 hex). Identificador. |
| `kind` | `release` (PR desde `develop`), `hotfix` (PR desde `hotfix/*`) u `other` (push sin PR, que no debería existir por la protección de rama: se trata como `release`). |
| `changed` | Lista de servicios desplegables a reconstruir (R2). Puede ser vacía. |

## Manifiesto de versiones (`versions.env`)

- Una variable `TAG_*` por cada servicio desplegable, siempre las 13 (no puede faltar
  ninguna: el compose usa `:?`).
- `DEPLOYED_SHA=<sha>` del release que lo generó.
- **Derivación:** manifiesto del release anterior + `TAG_<svc>=<sha>` para cada servicio de
  `changed`. En el primer despliegue, todas valen `<sha>`.
- **Invariante:** cada `TAG_*` apunta a una imagen `vito/<svc>:<tag>` cargada en el VPS.
  `deploy.sh` lo verifica antes de cambiar `current`.

## Bundle

Directorio `/opt/vito/releases/<sha>/` con los archivos de R4 y su `versions.env`.

| Estado | Significado |
|--------|-------------|
| `staged` | Copiado y validado (`config -q`), no activo. |
| `active` | `current` apunta aquí. Solo uno a la vez. |
| `previous` | Activo anterior; destino de la vuelta automática. |
| `retained` | Uno de los últimos 3 desplegados con éxito; destino válido de rollback. |
| `pruned` | Fuera de los últimos 3: se borran el directorio y las imágenes que ningún bundle retenido usa. |

## Despliegue

Transiciones:

```
pending ──(aprobación)──► approved ──(live-check ok)──► applying ──(salud ok)──► succeeded
   │                         │                              │
   │ (24 h sin aprobar)       │ (evento en vivo, sin force)  │ (salud falla)
   ▼                         ▼                              ▼
cancelled                 blocked                     rolled_back  (o failed si no hay previous)
```

Campos registrados (FR-010), una línea JSON en `/opt/vito/deployments.log` y el mismo
resumen en el *job summary* de GitHub:

| Campo | Tipo | Nota |
|-------|------|------|
| `at` | ISO-8601 UTC | inicio de `applying` |
| `type` | `deploy` · `rollback` | |
| `kind` | `release` · `hotfix` | solo en `deploy` |
| `sha` | string | release aplicado (en rollback: el destino) |
| `from_sha` | string · null | `DEPLOYED_SHA` anterior |
| `versions` | objeto `{servicio: tag}` | manifiesto resultante |
| `changed` | string[] | |
| `approver` | login de GitHub | |
| `triggered_by` | login de GitHub | |
| `reason` | string · null | obligatorio si hay autoaprobación o `force_live_event` |
| `forced_live_event` | bool | |
| `result` | `succeeded` · `rolled_back` · `failed` · `blocked` | |
| `duration_s` | entero | |
| `run_url` | URL | run de GitHub |

## Regla de alerta

| Campo | Ejemplo |
|-------|---------|
| `name` | `api-down` |
| `source` | synthetic · metrics · logs |
| `condition` | `probe_success == 0` |
| `for` | `2m` |
| `notify` | correo de equipo + correo de cada lead + Telegram |

Las cuatro reglas de FR-025 están en [contracts/observability.md](./contracts/observability.md).
