# Runbook · Despliegue y rollback del backend

Cómo operar el despliegue continuo de la [feature 001](../../specs/001-cd-produccion/spec.md):
`cd.yml` despliega cada merge a `main`, `rollback.yml` vuelve a un release anterior y
`expire-approvals.yml` cancela las aprobaciones que llevan más de 24 h esperando.
Aprovisionamiento previo: [aprovisionamiento.md](aprovisionamiento.md). Incidentes:
[incident.md](incident.md). Restauración de Mongo o del VPS: [restore.md](restore.md).

> **El repo es público.** No pegar en issues, PR ni chats la IP, el `.env`, salidas de
> `docker inspect` ni logs con datos de usuarios.

## Cómo fluye un release

1. Se mergea el PR de release `develop` → `main` (ADR-012). `cd.yml` arranca solo.
2. **Changes** compara contra el último despliegue exitoso y decide qué reconstruir. Si solo
   cambiaron documentación, frontend o CI, termina ahí sin pedir aprobación.
3. **Build** construye cada servicio cambiado y lo escanea con Trivy. Un CVE CRITICAL o HIGH
   con arreglo, y sin excepción en `.trivyignore`, corta el release antes de pedir aprobación.
4. **Deploy** espera la aprobación del Environment:
   - `production` (release): aprueba **el otro lead**. GitHub no deja que se apruebe a sí
     mismo quien disparó el run.
   - `production-hotfix` (PR desde `hotfix/*`): puede aprobar cualquiera de los dos. Si
     aprueba quien lo disparó, **tiene que escribir el motivo en el comentario de la
     aprobación**; sin comentario el job falla.
   - Una aprobación pendiente vence a las 24 h y avisa por Telegram. Para desplegar después,
     relanzar el run (*Re-run all jobs*).
5. Con la aprobación: SSH con huella fija, `.env` desde el secreto `DOTENV_PRODUCTION`,
   bundle a `/opt/vito/releases/<sha>/`, chequeo de evento en vivo, carga de imágenes y
   `deploy.sh apply`.
6. Si la salud falla, `deploy.sh` vuelve solo al release anterior y avisa.
7. Con éxito, el job **Tag** crea el tag git `v<VERSION>`.

## Leer el resultado

- **Resumen del run** (pestaña *Summary*): resultado y código, release y anterior,
  servicios reconstruidos, quién aprobó, motivo, duración y versión de cada servicio.
- **En el VPS** (solo un lead, con su llave):

  ```bash
  ssh deploy@<host> /opt/vito/current/infrastructure/deploy/remote/deploy.sh status
  tail -n 5 /opt/vito/deployments.log      # una línea JSON por despliegue o rollback
  cat /opt/vito/history                     # releases con éxito; los 3 últimos son destinos de rollback
  ```

## Qué hacer con cada código

| Código | Dónde | Qué pasó | Qué hacer |
|:------:|-------|----------|-----------|
| 2 | `deploy.sh` | Configuración inválida o falta un secreto en el `.env`. No se tocó nada. | Ver el error de `docker compose config` en el log del paso *Aplicar*. Corregir `DOTENV_PRODUCTION` en los tres Environments (y en el gestor) y relanzar. |
| 3 | live-check | Hay un evento publicado en curso (±1 h) o un stream activo. No se tocó nada. | Esperar a que termine y relanzar. Si es un hotfix urgente: ver *Forzar durante un evento en vivo*. |
| 4 | `deploy.sh` | Falta una imagen o el bundle, o el rollback pidió un SHA no retenido. No se tocó nada. | Relanzar el run completo (vuelve a construir y cargar). En un rollback, elegir uno de los SHA de `history`. |
| 5 | `deploy.sh` | La salud falló y se volvió solo al release anterior. Producción quedó como estaba. | Revisar en el log qué servicio no llegó a *healthy* (`docker compose ps`, logs en Grafana). Corregir con un PR normal o un hotfix. |
| 6 | `deploy.sh` | La salud falló y **no** se pudo volver (o no había anterior). Producción puede estar caída. | Ver *Código 6* abajo. Es el único caso que requiere un lead de inmediato. |
| 7 | live-check | No se pudo comprobar si hay un evento en vivo (Mongo caído, usuario de solo lectura). No se tocó nada. | Revisar `mongodb` en el VPS. Si producción está caída y hay que desplegar igual, forzar como en el código 3. |

### Código 6

1. `deploy.sh status`: qué release quedó en `current` y qué servicios no están *healthy*.
2. Si hay un release retenido sano, lanzar `rollback.yml` con ese SHA.
3. Si no lo hay (primer despliegue): `docker compose -p vito --project-directory /opt/vito/current
   -f /opt/vito/current/docker-compose.prod.yml --env-file /opt/vito/.env
   --env-file /opt/vito/current/versions.env logs --tail 200 <servicio>`, corregir y desplegar de nuevo.
   Los volúmenes de datos nunca se tocan: reintentar es seguro.

## Forzar durante un evento en vivo

Un release normal **nunca** se despliega durante un evento: hay que esperar. Solo un hotfix
o un rollback pueden forzarlo (FR-028):

- **Hotfix:** Actions → *ViTo CD* → *Run workflow* sobre `main`, con `force_live_event` y
  `reason`. El commit tiene que haber entrado por un PR `hotfix/*`; si no, falla antes de
  pedir aprobación.
- **Rollback:** `rollback.yml` con `force_live_event`; el motivo es `reason`.

El forzado queda en el registro (`forced_live_event: true` y el motivo) y avisa por Telegram
y correo. Cada servicio que se reinicia queda fuera menos de un minuto.

## Primer despliegue

- No hay release anterior: se construyen los 13 servicios y, si la salud falla, no hay a
  dónde volver (código 6).
- El chequeo de evento en vivo se omite (`{"skipped":"first-deploy"}` en el registro).
- Antes, correr la verificación previa: *ViTo CD* → *Run workflow* con `mode=preflight`.
  No pide aprobación ni reinicia nada.
- Después, verificar que Compose no resolvió el symlink `current` (research R4):
  `docker inspect vito-caddy-1 --format '{{range .Mounts}}{{.Source}} {{end}}'` debe mostrar
  `/opt/vito/current/...`.

## Rollback

Vuelve a uno de los **últimos 3 releases con éxito** sin reconstruir nada: cambia el bundle
activo (compose, configuración y versiones a la vez) y espera la salud.

1. **Elegir el SHA:** `cat /opt/vito/history` en el VPS, o en GitHub → *Deployments* →
   `production` (los despliegues con éxito, con su commit). Hace falta el SHA completo.
2. Actions → *ViTo Rollback* → *Run workflow* sobre `main` con `sha` y `reason`.
3. Lo aprueba cualquiera de los dos leads, también quien lo disparó (Environment
   `production-hotfix`, D-13). El motivo y quién aprobó quedan en `deployments.log`.
4. Si la salud del release destino falla, vuelve solo al que estaba (código 5).

**La base de datos no se revierte.** Un rollback devuelve el código, no los datos. Si el
release que se quiere deshacer cambió el formato de documentos o creó índices, el código
anterior tiene que poder leerlos; si no puede, hace falta un **hotfix hacia adelante** en
lugar de un rollback.

**Rollback o hotfix:**

| Situación | Qué usar |
|-----------|----------|
| El release nuevo rompe algo y el anterior funcionaba | Rollback |
| El error ya estaba en releases anteriores | Hotfix (`hotfix/*` desde `main`) |
| El release migró datos que el código anterior no entiende | Hotfix |
| El destino ya no está entre los 3 retenidos | Hotfix (o redesplegar ese commit con un PR) |

## Verificación previa (sin desplegar)

*ViTo CD* → *Run workflow* → `mode=preflight`. Usa el Environment `production-preflight`
(sin aprobación) y no espera a un despliegue pendiente. Comprueba la huella SSH, que estén
todos los secretos y `docker compose config -q` con el `.env` real (en una copia aparte, que
borra al terminar). Si falta un secreto, falla nombrando solo ese.
