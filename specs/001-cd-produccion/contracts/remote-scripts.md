# Contrato: scripts del VPS

Viven en `infrastructure/deploy/remote/`, viajan en el bundle y corren como `deploy` en
`/opt/vito`. Bash con `set -euo pipefail`. Ninguno imprime valores del `.env`.

## `deploy.sh`

```
deploy.sh apply    <sha> [--changed "svc1 svc2"] [--meta <json>]
deploy.sh rollback <sha> [--meta <json>]
deploy.sh check    <sha>
deploy.sh status
```

Todos los comandos corren `docker compose -p vito --project-directory /opt/vito/current` (R4),
aunque validen otro bundle: `config` no exige que `current` exista (primer despliegue).

Estado en `/opt/vito`: `history` (SHA desplegados con éxito, el último al final; de ahí
salen los 3 retenidos) y `deployments.log`. `apply` y `rollback` agregan una línea JSON al
registro en todos los resultados (también con código 2 o 4) y la repiten como última línea
de stdout para el *job summary*. `check` y `status` no escriben nada.

| Comando | Precondición | Efecto |
|---------|--------------|--------|
| `apply` | `releases/<sha>/` copiado e imágenes cargadas | genera `versions.env` desde el activo + `--changed` (con `RELEASE_VERSION` y los `CFG_*`), valida, activa, espera salud y, si falla, vuelve sola al anterior (R4) |
| `rollback` | `releases/<sha>/` retenido (últimos 3 con éxito) | activa ese bundle sin reconstruir, espera salud y, si falla, vuelve al que estaba |
| `check` | `releases/<sha>/` copiado | `config -q` con el `.env` real y `status`; no cambia `current` ni reinicia nada (verificación previa, R8) |
| `status` | — | imprime `DEPLOYED_SHA`, versiones por servicio y estado de salud (sin secretos) |

`--meta` lleva los campos que solo conoce GitHub (`approver`, `triggered_by`, `reason`,
`run_url`, `kind`, `forced_live_event`), que van al registro (data-model, *Despliegue*).

| Código de salida | Significado |
|:----------------:|-------------|
| 0 | `succeeded` |
| 2 | configuración inválida, secreto faltante o argumento inválido (SHA que no es de 40 hex, servicio desconocido en `--changed`, `--meta` que no es un objeto JSON): no se tocó nada (FR-012) |
| 4 | imagen de una versión no cargada, bundle inexistente o rollback a un SHA no retenido: no se tocó nada |
| 5 | salud falló y se volvió al anterior (`rolled_back`) |
| 6 | salud falló sin anterior o la vuelta también falló (`failed`): requiere un lead |

## `live-check.sh`

```
live-check.sh            # exit 0 = libre u omitido, 3 = bloqueado, 7 = no se pudo comprobar
```

Si no existe `/opt/vito/current` o el contenedor `mongodb` no está corriendo, sale con 0 e
imprime `{"skipped":"first-deploy"}` sin consultar nada (R6).

Imprime en stdout un JSON `{"events":[{"id","title"}],"streams":[{"id","eventId"}]}`. Usa
`vito_deploy_ro` (R6); su contraseña llega por la variable de entorno
`MONGO_DEPLOY_RO_PASSWORD` y nunca por argumento.

## `ensure-ro-user.sh`

Idempotente: crea o actualiza `vito_deploy_ro` con rol `read` en `vito_events` y
`vito_streaming`, usando las credenciales root del `.env`. `deploy` lo ejecuta antes de
`live-check.sh`.

## `bootstrap.sh` (fuera del bundle: `infrastructure/vps/bootstrap.sh`)

```
sudo ./bootstrap.sh --pubkey "<llave pública del pipeline>" [--pubkey "<llave de un lead>"]...
```

Idempotente (R7). Al final imprime un chequeo: Docker y Compose instalados, `ufw` activo con
las reglas, usuario `deploy` creado, contraseñas de SSH desactivadas y `/opt/vito` con sus
permisos. Correrlo dos veces no cambia nada (Historia 1, escenario 9).
