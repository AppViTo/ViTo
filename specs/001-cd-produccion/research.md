# Research: Despliegue continuo a producción (Fase Tracción)

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Fecha**: 2026-10-22

Cada sección resuelve una incógnita del *Technical Context*. Formato: Decisión ·
Justificación · Alternativas.

---

## R1 · Una versión por servicio (problema del `IMAGE_TAG` único)

**Decisión:** `docker-compose.prod.yml` reemplaza `${IMAGE_TAG:?}` por una variable por
servicio (`${TAG_AUTH_SERVICE:?}`, `${TAG_EVENT_SERVICE:?}`, …, `${TAG_MONGO_BACKUP:?}`).
Los valores viven en un **manifiesto de versiones** (`versions.env`) por despliegue, en
`/opt/vito/releases/<sha>/versions.env`. El manifiesto nuevo copia el anterior y solo
cambia los servicios reconstruidos. Compose se ejecuta con dos archivos de entorno:
`--env-file /opt/vito/.env --env-file /opt/vito/current/versions.env`.

**Justificación:** Compose recrea un contenedor solo si cambia su configuración (incluida
la imagen). Con un tag por servicio, `docker compose up -d` deja intactos los servicios no
cambiados y los datastores (FR-004, FR-011, SC-002) sin listas manuales de servicios. El
rollback es apuntar `current` a un manifiesto anterior (FR-008, sin reconstruir).

**Alternativas:**
- *Re-etiquetar las imágenes no cambiadas con el SHA nuevo:* rompe la trazabilidad (la misma
  imagen tendría muchos SHA) y obliga a cargar en el VPS imágenes que ya están ahí.
- *Construir siempre todo:* ~10 imágenes Java por release, ~25 min de CI y reinicio de todos
  los servicios (viola SC-001 y SC-002).

## R2 · Qué servicios cambiaron

**Decisión:** el job `changes` compara el commit actual con el **último despliegue exitoso**
(no con el commit anterior) y mapea rutas a servicios con un script versionado
(`infrastructure/deploy/changed-services.sh`). El último despliegue exitoso se obtiene de la
**API de Deployments de GitHub**: cada job con `environment:` registra uno, con su SHA y su
estado.

| Ruta cambiada | Servicios a reconstruir |
|---------------|------------------------|
| `backend/<svc>/**` | `<svc>` |
| `backend/Dockerfile`, `backend/Dockerfile.dockerignore` (y `backend/pom.xml`, `backend/shared-*/**` cuando exista T-B18) | los 10 Java |
| `ai-services/<svc>/**` | `<svc>` (transcode, moderation) |
| `infrastructure/backup/**` | `mongo-backup` |
| `docker-compose.prod.yml`, `infrastructure/caddy/**`, `infrastructure/docker/srs/**`, `infrastructure/observability/**` | ninguno: se sincroniza el bundle y Compose recrea lo que cambió de definición |
| solo `docs/**`, `specs/**`, `frontend/**` | ninguno: el despliegue de backend termina sin tocar el VPS |

Sin despliegue previo (primer despliegue), o si el SHA anterior no es ancestro del actual,
se reconstruye todo.

**Justificación:** comparar contra el commit anterior pierde cambios cuando un despliegue
falla o se cancela; contra el último exitoso nunca se salta nada. Leer el estado desde la
API de GitHub evita conectarse al VPS (y pedir secretos y aprobación) antes de saber si hay
algo que desplegar.

**Alternativas:** `dorny/paths-filter` (compara contra el push, no contra el último
despliegue); tag git `deployed/production` (requiere `contents: write` y se desincroniza si
el job falla después de moverlo).

## R3 · Construcción y transporte de imágenes

**Decisión:** el job `build` (sin secretos) construye con Buildx y caché `type=gha` (los
mismos *scopes* del job `docker-build` de CI), exporta cada imagen con
`docker save | zstd` y la sube como artifact con retención de 1 día. El job `deploy`
(después de la aprobación) descarga los artifacts y los carga en el VPS con
`zstd -d | ssh deploy@vps docker load`.

**Justificación:** construir antes de la aprobación hace que el tiempo entre aprobar y
tener producción sana sea solo transferencia y arranque (SC-001 ≤ 15 min). Sin registro,
como fija ADR-011 (j). El repo es público: los artifacts no consumen la cuota del plan.

**Alternativas:** construir en el VPS (consume CPU y RAM de producción y exige el código
fuente allí); GHCR (ADR-011 lo descartó para esta fase; sería la opción natural en Fase
Crecimiento).

## R4 · Qué se copia al VPS y en qué orden

**Decisión:** un **bundle** por despliegue en `/opt/vito/releases/<sha>/`, con
`docker-compose.prod.yml`, `infrastructure/caddy/`, `infrastructure/docker/srs/`,
`infrastructure/observability/`, `infrastructure/deploy/remote/*.sh` y `versions.env`.
`/opt/vito/current` es un symlink al bundle activo; `/opt/vito/.env` (secretos,
`chmod 600`) queda fuera de los bundles. Se conservan los últimos 3 bundles e imágenes
(edge case del disco).

Secuencia del script remoto `deploy.sh apply <sha>`:
1. `docker compose … config -q` con el bundle nuevo y el `.env` real; si falla, sale sin
   tocar nada (FR-012).
2. Cambiar `current` al bundle nuevo y `docker compose up -d --wait --wait-timeout 300`
   (FR-007).
3. Si falla: volver `current` al bundle anterior, repetir `up -d --wait` y salir con
   error (vuelta automática, aclaración 2).
4. Registrar una línea JSON en `/opt/vito/deployments.log` (FR-010) y podar los bundles e
   imágenes fuera de los últimos 3.

El primer despliegue no tiene bundle anterior: si falla, queda en error y avisa (aclaración
2); los volúmenes de datos no se tocan, así que reintentar es seguro.

**Justificación:** `--wait` usa los healthchecks que ya declara el compose (T-B14). Los
bundles hacen el rollback atómico: cambia el compose y las versiones a la vez.

**Alternativas:** `git pull` en el VPS (requiere credenciales y deja el código fuente en
producción); copiar solo `versions.env` (un cambio en el Caddyfile o en el compose no
llegaría).

## R5 · Aprobación del Environment y autoaprobación en hotfix

**Decisión:** dos Environments en GitHub.

| Environment | Revisores | *Prevent self-review* | Se usa para |
|-------------|-----------|:---------------------:|-------------|
| `production` | los dos leads | ✅ | releases, rollbacks, web |
| `production-hotfix` | los dos leads | ❌ | commits que entran a `main` desde un PR `hotfix/*` |

Los dos Environments tienen los mismos secretos. El job `changes` mira el PR asociado al
commit (`GET /repos/{repo}/commits/{sha}/pulls`) y elige el Environment. En
`production-hotfix`, un paso posterior a la aprobación consulta
`GET /actions/runs/{run_id}/approvals`. Si quien aprobó es quien disparó el run y el
comentario de la aprobación está vacío, el job falla: el motivo escrito es ese comentario
(FR-002), y se copia al registro de despliegue.

**Justificación:** GitHub no permite que *prevent self-review* dependa de la rama. Dos
Environments es la forma nativa de tener la regla general y la excepción. El comentario de
la aprobación ya queda en GitHub y es auditable.

**Alternativas:** un solo Environment sin *prevent self-review* más una verificación
propia (la regla quedaría en un script, no en GitHub); `workflow_dispatch` para cada
hotfix (agrega un paso manual justo cuando hay apuro).

## R6 · Bloqueo por evento en vivo (FR-028)

**Decisión:** antes de tocar servicios, el script remoto `live-check.sh` consulta Mongo en el
propio VPS con un **usuario de solo lectura** (`vito_deploy_ro`, rol `read` en
`vito_events` y `vito_streaming`):

- `vito_events.events`: `status = "PUBLISHED"` y `details.startDate ≤ ahora + 1 h` y
  `details.endDate ≥ ahora − 1 h` (margen por zona horaria: `LocalDateTime` sin zona).
- `vito_streaming.live_streams`: `status ∈ {LIVE, CONNECTING, BUFFERING}`.

Si hay coincidencias, sale con código 3 y la lista (id y título) para el aviso. Si la
consulta falla, también se bloquea (edge case). Para forzar, el `workflow_dispatch` recibe
`force_live_event: true` y `reason`. Solo se acepta en el Environment `production-hotfix`
(hotfix) o en un rollback, y el motivo queda en el registro.

**Justificación:** no hay una API interna para esto, y crearla exige contrato (Principio I)
y una ola. Una consulta de solo lectura desde herramientas de operación no es un servicio
que lea la base de otro (Principio II): se registra en *Complexity Tracking*.

**Alternativas:** endpoint interno `/internal/live-status` en event y streaming (más
correcto a largo plazo; se propone cuando haya Ola 4); ventana horaria fija (los leads la
descartaron en la aclaración 5).

## R7 · Acceso SSH y preparación del servidor

**Decisión:** script idempotente `infrastructure/vps/bootstrap.sh`, que corre un lead como
root una vez:
- Docker Engine y el plugin Compose desde el repositorio oficial; `zstd`, `ufw`,
  `fail2ban`, actualizaciones de seguridad desatendidas.
- Usuario `deploy` (grupo `docker`), `authorized_keys` con la llave pública del pipeline y
  de cada lead; `PasswordAuthentication no` y `PermitRootLogin prohibit-password`.
- `ufw`: `limit 22/tcp`, `80`, `443`, `1935/tcp`, `8000/udp`; todo lo demás denegado.
- `/opt/vito/{releases,backups}`, `.env` vacío con `chmod 600` y dueño `deploy`.
- Usuario Mongo de solo lectura, creado en el primer despliegue (R6), no en el bootstrap.

El pipeline fija la huella del servidor con el secreto `SSH_KNOWN_HOSTS` y
`StrictHostKeyChecking=yes` (FR-014).

**Desviación de T-I06:** T-I06 pedía el 22 "restringido". Las IP de los runners de GitHub no
son fijas, así que no se puede filtrar por IP. Queda restringido por llave (sin contraseña),
`ufw limit` y `fail2ban`.

**Alternativas:** Tailscale o Cloudflare Tunnel para SSH (cierra el 22 al público, pero
agrega una cuenta y un agente; se reconsidera en Fase Crecimiento); runner autoalojado en el
VPS (ejecutaría código de PR en producción; descartado con un repo público).

## R8 · Concurrencia

**Decisión:** `concurrency: { group: vps-production, cancel-in-progress: false }` en los
workflows de deploy y rollback del backend. La web usa su propio grupo `pages-production`:
no toca el VPS.

## R9 · Web en Cloudflare Pages

**Decisión:** `deploy-frontend.yml` pasa a `wrangler pages deploy build/web
--project-name=<var> --branch=main`, con `wrangler` fijado por versión, en el Environment
`production` (FR-021). Se reemplaza `vercel.json` por `web/_redirects`
(`/* /index.html 200`) y `web/_headers` (`X-Content-Type-Options`, `Referrer-Policy`,
`X-Frame-Options: DENY`, caché larga para `/assets/*`). `flutter build web` los copia al
artefacto. Antes de construir, el workflow falla si `VITO_API_BASE_URL` o
`VITO_WS_BASE_URL` (variables del Environment) están vacías (FR-019; cubre la parte de
despliegue de O-15, y T-F06 sigue a cargo del check dentro de `Env`). Secretos:
`CLOUDFLARE_API_TOKEN` (permiso *Pages: Edit*) y `CLOUDFLARE_ACCOUNT_ID`, que va como
secreto porque el repo es público. Cuando la primera publicación queda verificada se borran
el job de Vercel, `vercel.json` y los secretos `VERCEL_*` (FR-022).

**Alternativas:** integración Git nativa de Pages (construye en Cloudflare sin las puertas de
CI ni la aprobación del Environment).

## R10 · Observabilidad con Grafana Cloud (plan gratuito)

**Decisión:** un contenedor **Grafana Alloy** en `docker-compose.prod.yml`
(`mem_limit: 256m`) que:
- **Métricas:** extrae `/actuator/prometheus` de los 10 servicios Java (el gateway en el
  puerto de gestión 9080) y el host y los contenedores con los exportadores integrados de
  Alloy (`prometheus.exporter.unix`, `prometheus.exporter.cadvisor`). Lista blanca de
  métricas para quedar por debajo de 10.000 series activas.
- **Logs:** `loki.source.docker` a través de `tecnativa/docker-socket-proxy`, que solo
  permite `GET /containers` e `/info`. Alloy **no** monta el socket de Docker: un montaje
  `:ro` del socket no limita la API, y quien la tiene controla el host (Principio III). Hay
  una etapa `stage.replace` que enmascara `Bearer …`, `token=…`, `password…` y correos
  (FR-026). Etiquetas solo `service` y `level`.
- **Trazas:** receptor OTLP en Alloy. Los servicios Spring agregan
  `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` con muestreo del 10 %.
  Es un cambio de código en 10 POMs: tarea propia, alineada con T-B18 si el parent POM ya
  existe.
- **Uptime:** *Synthetic Monitoring* de Grafana Cloud (gratuito) contra
  `https://api.<dominio>/healthz`. Caddy agrega esa ruta, que responde con el readiness del
  gateway sin exponer el resto del actuator.
- **Alertas** (Grafana Cloud Alerting, contact points correo + Telegram): las cuatro de
  FR-025. La del backup es una regla de logs: no hay línea `OK ` de `mongo-backup` en 26 h.
- **Tablero RED del gateway** en `infrastructure/observability/dashboards/*.json`, importado
  a mano. Las alertas como código quedan para Fase Crecimiento (Perfiles de Despliegue).

Límites del plan gratuito que hay que respetar: 10k series, 50 GB de logs, 50 GB de trazas,
retención de 14 días.

Memoria: con Alloy (256m) y el proxy (32m), el total pasa de 9.344 MiB a ~9.630 MiB, todavía dentro de ~11,7 GiB.

**Alternativas:** agente OTel Java (`-javaagent`, ~60–100 MiB más por servicio: no cabe en
512m); Prometheus y Loki propios (memoria y mantenimiento, descartado por ADR-011 h).

## R11 · Avisos del pipeline

**Decisión:** los fallos de despliegue, las vueltas automáticas y los bloqueos por evento
avisan desde el propio workflow al grupo de Telegram (Bot API,
`TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`) y por correo con Resend a los dos leads
(`RESEND_API_KEY`, `ALERT_EMAILS`). Mientras Resend no esté verificado, solo Telegram. Es el
mismo canal doble de la aclaración 3.

## R12 · Resend (FR-017)

**Decisión:** dominio de envío `send.<dominio>`. Los registros SPF, DKIM y MX de retorno que
da Resend se agregan en el DNS de Cloudflare, más `_dmarc.<dominio>` con `p=none` al
principio. La llave `RESEND_API_KEY` va al Environment y al gestor. Plan gratuito: 3.000
correos al mes, 100 al día. La integración en la app es otra feature.

## R13 · Secretos fuera del repo (SC-007)

**Decisión:** activar *Secret scanning* y *Push protection* de GitHub (gratuitos en repos
públicos). Es una acción manual de los leads y se suma a la guía de T-I06. El workflow
nunca hace `echo` de valores. Usa `::add-mask::` para los datos derivados (IP, host) y
escribe la llave SSH en un archivo `0600` que borra al terminar.

**Alternativas:** job `gitleaks` en `ci.yml` (agrega un check obligatorio a la protección de
rama, CONTRIBUTING §1; queda como mejora si el escaneo nativo resulta insuficiente).

## R14 · Imágenes faltantes en CI

**Hallazgo:** el job `docker-build` de `ci.yml` no construye `moderation-service` ni
`mongo-backup`, pero el compose de producción sí los usa. El CD los construye (R2); además,
se agregan a la matriz de CI para detectar Dockerfiles rotos antes de `main`. Cambia el
nombre de los checks: hay que actualizar la protección de rama en el mismo PR.

## R15 · Fuente del `.env` de producción

**Decisión:** el `.env` completo vive como un único secreto `DOTENV_PRODUCTION` en los dos
Environments. El job `deploy` lo escribe en `/opt/vito/.env` (`0600`, dueño `deploy`) en cada
despliegue, antes de validar la configuración. Una copia vive en el gestor de contraseñas
(ADR-013).

**Justificación:** una sola fuente de verdad. Cambiar un secreto es editar el Environment y
redesplegar, sin entrar por SSH. Además, un release que necesita una variable nueva falla en
`config -q` (FR-012) y no a mitad de camino.

**Alternativas:** editar el `.env` a mano en el VPS (deriva entre el VPS, el gestor y GitHub,
y exige SSH de un lead); un secreto por variable (unas 25 entradas, que hay que mantener
iguales en dos Environments).

## R16 · Sistema operativo del VPS

**Supuesto:** Ubuntu 24.04 LTS (imagen por defecto de Contabo). `bootstrap.sh` aborta si la
distribución no es Ubuntu 22.04 o 24.04. Hay que confirmarlo con los leads en T-I06.
