# Runbook · Incidentes en producción (T-I14)

Qué mirar y qué hacer cuando algo falla en producción (ADR-011: un VPS con Docker Compose
detrás de Caddy). **Hasta T-I13 no hay Grafana ni alertas:** todo se diagnostica por SSH con
los comandos de esta guía. La ejecuta un lead con su llave (ADR-013).

Relacionados: [despliegue.md](despliegue.md) (pipeline, rollback, códigos de `deploy.sh`),
[restore.md](restore.md) (restaurar Mongo o el VPS) y
[aprovisionamiento.md](aprovisionamiento.md) (DNS, secretos, puertos).

> **El repo es público.** `<VPS>` es la IP, `<DOMAIN>` el dominio raíz. No pegar en
> issues, PR ni chats la IP, el `.env`, tokens, salidas de `docker inspect` ni logs con
> datos de usuarios (correos, nombres, IDs de eventos privados).

## 0. Antes del evento

El día anterior y una hora antes, un lead:

- [ ] **Sin releases el día del evento.** No mergear a `main`. `cd.yml` y `rollback.yml`
      se bloquean solos con un evento publicado en curso (±1 h) o un stream activo
      (código 3), pero un release que entra antes de esa ventana no se bloquea y deja poco
      margen para descubrir sus fallos.
- [ ] **Al menos un lead de guardia, y el otro localizable.** Un rollback o un hotfix
      (Environment `production-hotfix`, D-13) los puede aprobar quien los dispara, con
      motivo. Un release normal (`production`) sigue necesitando al otro lead.
- [ ] **Estado sano:** [triage](#1-triage-en-5-minutos) completo, todo *healthy*, sin
      reinicios recientes, disco < 80 %, RAM con margen.
- [ ] **Smoke automático en verde:** Actions → *ViTo Smoke E2E* → *Run workflow* (o
      `node scripts/smoke-e2e.mjs` con `E2E_*` en el entorno); termina en `SMOKE OK`. El
      cron lo corre cada 15 min y `cd.yml` tras cada despliegue. Cubre login, listado,
      detalle, refresh y revocación por logout; **no** cubre QR, WebSocket ni streaming.
- [ ] **Backup fresco:** `dc exec -T mongo-backup entrypoint.sh backup` termina en `OK …`.
- [ ] **SHA de rollback a mano:** `cat /opt/vito/history` (el penúltimo es el anterior al
      activo). Guardarlo completo.
- [ ] **Prueba de punta a punta** desde un teléfono fuera de la red del VPS: login, abrir
      el evento, check-in con QR (WebSocket) y, si hay streaming, publicar 1 minuto por
      RTMP y verlo.
- [ ] Una terminal abierta con SSH al VPS y la función `dc` cargada; el grupo de Telegram
      de avisos abierto.

## Acceso y la función `dc`

```bash
ssh deploy@<VPS>
dc() {
  docker compose -p vito --project-directory /opt/vito/current \
    -f /opt/vito/current/docker-compose.prod.yml \
    --env-file /opt/vito/.env --env-file /opt/vito/current/versions.env "$@"
}
```

Es la misma invocación que usa [`deploy.sh`](../../infrastructure/deploy/remote/deploy.sh).
Los servicios de Compose son: `caddy`, `mongodb`, `redis`, `kafka`, `mongo-backup`, `srs`,
`api-gateway`, `auth-service`, `social-service`, `event-service`, `checkin-service`,
`mosaic-service`, `streaming-service`, `archive-service`, `chat-service`, `media-service`,
`transcode-service` y `moderation-service`.

```
cliente ─► Caddy api.<DOMAIN> :443 ─► api-gateway:8080 ─► servicio (/api/v1/**, /ws/**)
cliente ─► Caddy live.<DOMAIN> :443 ─► srs (WHIP/WHEP /rtc/, HLS/FLV)
host del evento ─► live.<DOMAIN>:1935 (RTMP) y 8000/udp (WebRTC) ─► srs, sin Caddy
fotos y videos ─► media.<DOMAIN> (R2, no pasa por el VPS)
```

## 1. Triage en 5 minutos

```bash
/opt/vito/current/infrastructure/deploy/remote/deploy.sh status   # release activo y estado de cada servicio
tail -n 3 /opt/vito/deployments.log                                # ¿hubo un despliegue o rollback reciente?

# Reinicios y OOM (los Java salen con ExitOnOutOfMemoryError y Docker los reinicia)
docker inspect --format '{{.Name}} reinicios={{.RestartCount}} oom={{.State.OOMKilled}} desde={{.State.StartedAt}}' \
  $(docker ps -aq --filter label=com.docker.compose.project=vito)

free -m                                     # RAM del host
docker stats --no-stream --format 'table {{.Name}}\t{{.MemUsage}}\t{{.CPUPerc}}'
df -h /                                     # disco
docker system df                            # cuánto ocupan imágenes, volúmenes y caché
```

Decidir con eso:

| Lo que se ve | Ir a |
|--------------|------|
| Empezó justo después de un despliegue | [4.2 Rollback](#42-rollback) |
| Un servicio `unhealthy`, `restarting` o con `oom=true` | [3. Por síntoma](#3-por-síntoma) y [4.1 Reiniciar](#41-reiniciar-un-servicio) |
| `api.<DOMAIN>` responde `502` | [3.1](#31-la-api-no-responde-502-503-504) |
| Fallan los tiempos reales (check-in, mosaico, chat, reacciones) | [3.2](#32-websocket) |
| Fallan fotos o videos | [3.3](#33-subida-y-medios) |
| Falla el streaming | [3.4](#34-streaming-srs) |
| Disco > 90 % o RAM agotada | [3.6](#36-disco-o-memoria) |
| Datos borrados o corruptos | [restore.md, escenario A](restore.md#a-restaurar-mongo-en-el-vps-actual) |

## 2. Salud por servicio

El Actuator no se publica fuera del VPS (T-B14): se consulta desde dentro de cada
contenedor, igual que los healthchecks del compose.

```bash
# Gateway: puerto de gestión 9080, con detalle (Redis, circuit breakers)
dc exec -T api-gateway curl -fsS http://localhost:9080/actuator/health
dc exec -T api-gateway curl -fsS http://localhost:9080/actuator/circuitbreakers

# Servicios Java: readiness en su propio puerto
for s in auth-service:8081 social-service:8082 event-service:8083 checkin-service:8084 \
         mosaic-service:8085 streaming-service:8086 archive-service:8087 chat-service:8088 \
         media-service:8089; do
  printf '%-18s ' "${s%%:*}"
  dc exec -T "${s%%:*}" curl -fsS "http://localhost:${s##*:}/actuator/health/readiness" || printf 'FALLA'
  echo
done

# Python
dc exec -T transcode-service python -c "import urllib.request; print(urllib.request.urlopen('http://localhost:8000/health', timeout=5).read())"
dc exec -T moderation-service python -c "import urllib.request; print(urllib.request.urlopen('http://localhost:8002/health', timeout=5).read())"

# Datos
dc exec -T mongodb mongosh --quiet --eval "db.adminCommand('ping').ok"                       # 1
dc exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli ping'                     # PONG
dc exec -T redis sh -c 'REDISCLI_AUTH="$REDIS_PASSWORD" redis-cli info memory' | grep -E '^(used_memory_human|maxmemory_human)'
dc ps kafka                                                                                  # healthy = escucha en 9092
```

Un circuit breaker `OPEN` en el gateway significa que ese servicio falló seguido: el
gateway responde `503 SERVICE_UNAVAILABLE` sin llamarlo hasta que se recupere.

### Logs

Cada contenedor guarda como mucho 30 MB de log (`json-file`, 3 × 10 MB); con carga, el del
gateway rota en minutos. **Guardar lo que interese apenas aparece** (en el VPS, no en el
repo ni en un chat).

```bash
dc logs --since 15m --tail 300 <servicio>
dc logs -f --tail 50 <servicio>                                   # en vivo
dc logs --since 30m <servicio> 2>&1 | grep -iE 'error|exception|refused|timeout' | tail -n 50
dc logs --since 1h <servicio> > /tmp/incidente-<servicio>.log     # copia antes de que rote
```

## 3. Por síntoma

### 3.1 La API no responde (`502`, `503`, `504`)

- `502` → Caddy no llega al gateway: `dc ps api-gateway`, `dc logs --since 15m api-gateway`.
  Si está caído o `unhealthy`, [reiniciarlo](#41-reiniciar-un-servicio).
- `503` con el envelope de error → el gateway está bien, falla el servicio de detrás
  (circuit breaker abierto o servicio caído): [salud](#2-salud-por-servicio) de ese servicio.
- No conecta o error de certificado → Caddy: `dc ps caddy`,
  `dc logs --since 30m caddy | grep -iE 'error|acme|certificate' | tail -n 30`. El DNS de
  `api` tiene que apuntar al VPS y estar en *DNS only* ([aprovisionamiento, paso 5](aprovisionamiento.md#5-dns-en-cloudflare)).
- Todos los Java dependen de `mongodb`, `redis` y `kafka`: si uno de esos cae, fallan
  muchos a la vez. Revisar primero los datos (sección 2).
- Login falla y Redis responde `OOM command not allowed`: Redis llegó a su tope (192 MB,
  `noeviction`, guarda la lista negra de tokens). Es un incidente de capacidad: avisar al
  otro lead; no borrar claves a mano.

### 3.2 WebSocket

Rutas: `/ws/chat`, `/ws/checkin/{eventId}`, `/ws/mosaic/{eventId}`,
`/ws/streaming/{streamId}`, siempre por `api.<DOMAIN>` y con `?token=<accessToken>`
([CONVENTIONS §7](../api/CONVENTIONS.md#7-websockets--realtime)). Camino: Caddy →
`api-gateway` (valida el token) → servicio.

1. ¿El servicio está sano? Salud de `checkin-service`, `mosaic-service`, `chat-service` o
   `streaming-service` (sección 2) y sus logs.
2. ¿Cómo se cierra la conexión?
   - Cierre `4401` → token inválido o vencido: el cliente tiene que renovar sesión. Si les
     pasa a todos a la vez, revisar `auth-service` y que `JWT_SECRET` sea el mismo en todos
     (viene del mismo `.env`; un cambio parcial no es posible salvo edición manual).
   - `403` en el handshake desde la web → el `Origin` no está en `CORS_ALLOWED_ORIGINS`
     (gateway y servicios WS). La app móvil no manda `Origin` y no se ve afectada.
   - `404` en el handshake → ruta mal escrita en el cliente.
3. Probar el handshake desde fuera (con un token propio de prueba; **no** pegarlo en
   ningún lado):

   ```bash
   read -rs TOKEN
   curl -sS --http1.1 -o /dev/null -w '%{http_code}\n' --max-time 5 \
     -H 'Connection: Upgrade' -H 'Upgrade: websocket' -H 'Sec-WebSocket-Version: 13' \
     -H "Sec-WebSocket-Key: $(openssl rand -base64 16)" \
     "https://api.<DOMAIN>/ws/chat?token=$TOKEN"
   ```

   `101` = el camino Caddy → gateway → servicio funciona (curl corta a los 5 s: es
   esperado).
4. Reiniciar `api-gateway` o un servicio WS **corta todas sus conexiones**; los clientes
   reconectan solos. Hacerlo solo si el servicio está mal, no "por si acaso".

### 3.3 Subida y medios

- **En producción la subida está cerrada a propósito** (T-I17): `POST /api/v1/media/**` y
  las escrituras de `/api/v1/mosaic/**` responden `404 NOT_FOUND` desde el gateway mientras
  `MOSAIC_UPLOADS_ENABLED` no valga `true` (T-I21). Un usuario que no puede subir fotos con
  la variable en `false` **no** es un incidente: es lo esperado. Con `true` se abren
  `POST /media/upload`, `POST /mosaic/post`, `POST /mosaic/grid/{eventId}/initialize` y
  `POST`/`DELETE /mosaic/item/{itemId}/like`; lo demás sigue en `404`. Para volver a
  cerrarla: `MOSAIC_UPLOADS_ENABLED=false` en `DOTENV_PRODUCTION` y redesplegar.
- Las fotos y videos existentes se sirven desde R2 (`media.<DOMAIN>`), sin pasar por el
  VPS. Si no cargan: `curl -sI https://media.<DOMAIN>/<una clave conocida>`. Si R2 falla,
  no hay nada que hacer en el VPS: revisar el estado de Cloudflare.
- URLs de medios mal formadas o errores de `/api/v1/media/**` en lectura: salud y logs de
  `media-service` (credenciales `S3_*`, `S3_PUBLIC_URL`).
- Procesado (miniaturas, transcodificación): `dc logs --since 30m transcode-service`; usa
  Kafka y R2.

### 3.4 Streaming (SRS)

El host publica por RTMP a `live.<DOMAIN>:1935` (la URL completa la entrega
`streaming-service`); los espectadores ven por WebRTC (WHEP en `/rtc/` + `8000/udp`) o por
HLS/FLV a través de Caddy.

```bash
dc ps srs streaming-service
dc logs --since 15m srs | tail -n 100
# API de gestión de SRS (no se publica fuera): desde streaming-service, que la usa
dc exec -T streaming-service curl -fsS http://srs:1985/api/v1/summaries
dc exec -T streaming-service curl -fsS http://srs:1985/api/v1/streams/     # ¿llega la publicación del host?
ss -lntu | grep -E ':(1935|8000)\b'                                         # puertos escuchando en el host
```

| Síntoma | Qué revisar |
|---------|-------------|
| El host no puede publicar | Desde la red del host: `nc -vz live.<DOMAIN> 1935`. Si no conecta: DNS de `live` (*DNS only*) y `ufw` (como root, `ufw status`: `1935/tcp` abierto). Si conecta pero no aparece en `/api/v1/streams/`: URL o clave de stream del cliente; `dc logs streaming-service`. |
| El stream existe pero WebRTC no reproduce | `SRS_CANDIDATE` del `.env` tiene que ser la IP pública actual del VPS; `8000/udp` abierto en `ufw`. Redes que bloquean UDP (wifi corporativo, algunos datos móviles) no pueden ver por WebRTC. |
| WebRTC no, HLS sí | Es lo anterior: `curl -s -o /dev/null -w '%{http_code}' https://live.<DOMAIN>/<app>/<stream>.m3u8` da `200` si HLS funciona (latencia mayor, ~10 s). |
| `live.<DOMAIN>` devuelve `404` en todo | Esperado fuera de `/rtc/*`, `*.m3u8`, `*.ts` y `*.flv` (lista blanca del Caddyfile). |
| Reacciones no llegan | Es WebSocket (`/ws/streaming/{streamId}`): sección 3.2. |

Reiniciar `srs` corta **todas** las transmisiones; el host tiene que volver a publicar.

### 3.5 Base de datos y backup

- `mongodb` caído o `unhealthy`: `dc logs --since 30m mongodb`. Disco lleno es la causa más
  común (3.6). Reiniciarlo deja sin servicio a todos los Java mientras arranca (hasta
  ~90 s).
- **Backup diario:** un fallo solo queda en el log, nadie recibe aviso. Comprobar cada día:
  `dc logs --since 26h mongo-backup | grep -E 'OK|ERROR'` y
  `dc exec -T mongo-backup entrypoint.sh list` (debe haber uno de hoy, 03:00 UTC).
- Datos borrados o corruptos: [restore.md, escenario A](restore.md#a-restaurar-mongo-en-el-vps-actual).
  Durante un evento, perder hasta 24 h de RSVP y check-ins suele ser peor que el problema:
  decidirlo entre los dos leads.

### 3.6 Disco o memoria

- **Disco:** `df -h /` y `docker system df`. Los logs ya están acotados. Se puede liberar
  sin riesgo con `docker image prune -f` (imágenes sin tag) y `docker builder prune -f`
  (caché de compilación). **No** borrar imágenes `vito/*` a mano: las de los 3 últimos
  releases son los destinos de rollback (`deploy.sh` las poda solo).
- **Memoria:** los límites suman ~9,1 GiB de ~11,7 GiB. Un Java con `oom=true` o muchos
  reinicios está en su tope de 512 MB: reiniciarlo da tiempo, pero el arreglo es un hotfix.
  Si es el host el que se queda sin RAM (`free -m`), no lanzar nada más (ni la prueba de
  restauración ni compilaciones).

## 4. Acciones

### 4.1 Reiniciar un servicio

```bash
dc restart <servicio>                    # mismo contenedor; rápido
dc up -d --wait --no-deps <servicio>     # recrea el contenedor con la definición del release activo
dc ps <servicio>                         # hasta healthy (los Java tardan hasta ~2 min)
```

- `restart` no aplica cambios del `.env`; `up -d --no-deps` sí, pero el `.env` del VPS lo
  sobrescribe el próximo despliegue o rollback con `DOTENV_PRODUCTION`. Un cambio de
  configuración se hace en el secreto (y en el gestor), no editando `/opt/vito/.env`.
- Qué corta cada reinicio: `api-gateway` → toda la API y todos los WebSocket; `srs` → todos
  los streams; `mongodb`, `redis` o `kafka` → todos los Java mientras vuelven; un servicio
  de negocio → solo su parte.

### 4.2 Rollback

Si el problema empezó con el último release y el anterior funcionaba: Actions →
*ViTo Rollback* con el `sha` de `/opt/vito/history`, `reason` y, **durante un evento**,
`force_live_event`. Lo puede aprobar quien lo dispara (`production-hotfix`, D-13); si el
otro lead está disponible, avisarle igual en el grupo. Detalle, cuándo conviene un hotfix y qué pasa
con los datos: [despliegue.md, Rollback](despliegue.md#rollback) y
[Forzar durante un evento en vivo](despliegue.md#forzar-durante-un-evento-en-vivo).

Si `deploy.sh` terminó en código 6 (producción caída sin vuelta automática):
[despliegue.md, Código 6](despliegue.md#código-6).

### 4.3 Restaurar

- Solo Mongo: [restore.md, escenario A](restore.md#a-restaurar-mongo-en-el-vps-actual).
- VPS perdido: [restore.md, escenario B](restore.md#b-reconstruir-el-vps-desde-cero).

### No hacer

- `dc down -v` o `docker volume rm`: **borra los datos** (Mongo, Redis, Kafka, certificados).
- `docker system prune -a` o `--volumes`: borra los destinos de rollback o los datos.
- `dc down` durante un evento: para todo; para un servicio, `dc restart <servicio>`.
- Editar archivos de `/opt/vito/releases/` o `/opt/vito/current/`: el próximo despliegue
  no los conoce. Todo cambio va por PR (o hotfix).
- Pegar logs, el `.env` o tokens en el grupo de Telegram o en GitHub.

## 5. A quién avisar y después

**Durante:**

- Quien detecta el problema avisa en el **grupo de Telegram de avisos** (el mismo de las
  alertas del pipeline) con: qué se ve, desde cuándo y qué va a hacer. Sin datos sensibles.
- Restauración de Mongo o un hotfix forzado **necesitan al otro lead** (decisión
  compartida); llamarlo si no responde en el grupo. Un rollback lo puede aprobar un solo
  lead (D-13), pero avisa antes en el grupo.
- Un solo lead opera la terminal a la vez; el otro mira y anota la hora de cada acción.
- Si el incidente afecta a los asistentes, el organizador del evento recibe un mensaje
  breve: qué no funciona y qué hacer mientras (p. ej. check-in manual por lista).

**Después (dentro de las 48 h):** un issue en GitHub con:

- Línea de tiempo (detección, acciones, recuperación), con horas UTC.
- Causa, impacto (qué parte de la app, cuánto tiempo, datos perdidos) y cómo se detectó.
- Qué se cambia para que no se repita: tareas nuevas en
  [`docs/roadmap/tasks.md`](../roadmap/tasks.md) y, si hizo falta un paso que no estaba
  aquí, el PR que actualiza este runbook.

Sin IP, dominio, tokens, logs crudos ni datos de usuarios.
