# Runbook · Restauración (T-I14)

Cómo recuperar producción (ADR-011: un VPS con Docker Compose) cuando se pierden los datos
de Mongo o el VPS entero. Objetivo de la constitución (Principio IX): **RPO ≤ 24 h,
RTO ≤ 4 h**. Lo ejecuta un lead con su llave SSH (ADR-013).

Relacionados: [aprovisionamiento.md](aprovisionamiento.md) (VPS nuevo, secretos, DNS),
[despliegue.md](despliegue.md) (pipeline, rollback, códigos de `deploy.sh`) e
[incident.md](incident.md) (qué mirar durante un incidente).

> **El repo es público.** En este documento `<VPS>` es la IP del VPS, `<DOMAIN>` el dominio
> raíz y `<SHA>` el SHA completo de un release. No pegar valores reales, el `.env`, salidas
> de `docker inspect` ni logs con datos de usuarios en issues, PR o chats.

| Escenario | Cuándo | Sección |
|-----------|--------|---------|
| A | El VPS funciona pero los datos de Mongo están mal (borrado, corrupción) | [A. Restaurar Mongo en el VPS actual](#a-restaurar-mongo-en-el-vps-actual) |
| B | El VPS se perdió o hay que mudarse a otro | [B. Reconstruir el VPS desde cero](#b-reconstruir-el-vps-desde-cero) |
| C | Prueba mensual de ADR-011 y ensayo de T-I14 | [C. Pruebas y ensayo](#c-pruebas-y-ensayo) |

## Qué hay y dónde

| Pieza | Dónde vive | Se recupera con |
|-------|------------|-----------------|
| Código y configuración (`docker-compose.prod.yml`, Caddyfile, `srs.conf`, scripts) | GitHub | el SHA del release |
| `.env` de producción | secreto `DOTENV_PRODUCTION` de los 3 Environments + copia en el gestor de contraseñas | la copia del gestor (los secretos de GitHub no se pueden leer) |
| Datos de Mongo | R2, bucket `vito-backups`, prefijo `mongo/`, un `vito-<UTC>.archive.gz` por día (03:00 UTC, retención 14 días) | `mongo-backup` (`restore.sh`) |
| Fotos y videos | R2, bucket `vito-media` (nunca estuvieron en el VPS) | nada: siguen ahí |
| Imágenes Docker | **solo en el VPS** (no hay registro, ADR-011) | se reconstruyen desde el código (B.4) |
| Redis, Kafka, certificados de Caddy | volúmenes del VPS, **sin backup** | se regeneran vacíos (ver *Qué se pierde*) |

**Qué se pierde siempre** al restaurar desde backup:

- Lo escrito en Mongo desde el último backup (hasta 24 h): RSVP, check-ins, mensajes.
- La lista negra de tokens de Redis: un token revocado vuelve a valer hasta que vence. Si el
  incidente fue un compromiso de cuentas, cambiar `JWT_SECRET` (cierra todas las sesiones).
- Los mensajes de Kafka que no se habían consumido.
- Caddy pide certificados nuevos a Let's Encrypt (unos segundos, si el DNS ya apunta).

Todos los comandos en el VPS usan esta función (como `deploy`; pegarla al entrar):

```bash
dc() {
  docker compose -p vito --project-directory /opt/vito/current \
    -f /opt/vito/current/docker-compose.prod.yml \
    --env-file /opt/vito/.env --env-file /opt/vito/current/versions.env "$@"
}
```

Es la misma invocación que usa [`deploy.sh`](../../infrastructure/deploy/remote/deploy.sh).

---

## A. Restaurar Mongo en el VPS actual

`restore.sh` hace `mongorestore --drop`: **reemplaza** las colecciones que vienen en el
backup. Es el último recurso; durante un evento en vivo, primero leer
[incident.md](incident.md).

1. **Ver los backups disponibles** (nombre con la fecha UTC, el último es el más nuevo):

   ```bash
   dc exec -T mongo-backup entrypoint.sh list
   ```

2. **Guardar el estado actual** antes de pisarlo, para poder volver atrás:

   ```bash
   dc exec -T mongo-backup entrypoint.sh backup     # termina con "OK store:…/vito-<UTC>.archive.gz"
   ```

   Ojo: este backup pasa a ser el más nuevo. En el paso 4 usar el **nombre** del backup
   bueno, no `latest`.

3. **Cortar el tráfico** para que nadie escriba durante la restauración. Caddy responde
   `502` mientras el gateway está parado:

   ```bash
   dc stop api-gateway
   ```

4. **Restaurar** (unos minutos; depende del tamaño):

   ```bash
   dc exec -T mongo-backup entrypoint.sh restore --yes vito-<UTC>.archive.gz
   # termina con "OK restaurado vito-<UTC>.archive.gz"
   ```

   `admin.*` no se restaura: los usuarios de Mongo los crea `MONGO_INITDB_*` y
   `ensure-ro-user.sh` (en cada despliegue o rollback).

5. **Comprobar** que están las bases y devolver el tráfico:

   ```bash
   dc exec -T mongodb sh -c 'mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" \
     --authenticationDatabase admin --eval "db.adminCommand({listDatabases: 1}).databases.forEach(d => print(d.name, d.sizeOnDisk))"'
   dc start api-gateway
   /opt/vito/current/infrastructure/deploy/remote/deploy.sh status   # todo healthy
   ```

6. Avisar en el grupo de Telegram y registrar el incidente ([incident.md](incident.md#5-a-quién-avisar-y-después)).

---

## B. Reconstruir el VPS desde cero

Tiempos de referencia en la tabla de [C.2](#c2-ensayo-de-t-i14-rto); el cuello de botella es
compilar las 13 imágenes (B.4).

### B.1 Servidor nuevo

[aprovisionamiento.md](aprovisionamiento.md), pasos 1 y 2: llave personal en root, huella
del servidor desde la consola VNC y `bootstrap.sh` con **todas** las llaves de `deploy`
(la del pipeline sale del gestor). Termina con todo en `[OK]`.

### B.2 Apuntar todo al servidor nuevo

Si la IP cambió:

- DNS en Cloudflare: registros `A` de `api` y `live` → nueva IP, *DNS only*
  ([aprovisionamiento.md, paso 5](aprovisionamiento.md#5-dns-en-cloudflare)).
- En el gestor, en el `.env`: `SRS_CANDIDATE` = nueva IP. Cargar el `.env` actualizado en
  `DOTENV_PRODUCTION` de los 3 Environments.
- `SSH_HOST` y `SSH_KNOWN_HOSTS` (huella nueva) en los 3 Environments
  ([paso 4](aprovisionamiento.md#4-secretos-y-variables)).

### B.3 `.env` y SHA del release

1. Copiar el `.env` del gestor al VPS sin dejarlo en la máquina del lead:

   ```bash
   # en la máquina del lead, con el .env guardado temporalmente en env.prod
   ssh deploy@<VPS> 'umask 077 && cat > /opt/vito/.env' < env.prod && rm -f env.prod
   ```

2. **SHA a restaurar:** el último despliegue con éxito. Desde la máquina del lead (misma
   consulta que el job *Changes* de `cd.yml`):

   ```bash
   for env in production production-hotfix; do
     gh api "repos/AppViTo/ViTo/deployments?environment=$env&per_page=30" \
       --jq '.[] | "\(.created_at) \(.sha) \(.statuses_url)"'
   done | sort -r | while read -r at sha url; do
     [ "$(gh api "$url?per_page=1" --jq '.[0].state // ""')" = success ] && { echo "$at $sha"; break; }
   done
   ```

   También se ve en GitHub → *Deployments* → `production`.

### B.4 Código, bundle e imágenes (en el VPS, como `deploy`)

**Camino recomendado: que las imágenes las construya el CD.** Desde GitHub Actions, ejecutar el workflow **ViTo CD** con `mode=deploy` y `rebuild_all=true` sobre `main`. Reconstruye las 13 imágenes, las escanea con Trivy (en modo informe hasta el 2026-11-07, excepción A9) y las sube como artifacts; `deploy.sh` las carga en el VPS. Así no se compila en el VPS.

El resto de esta sección es el plan de contingencia, para cuando el CD no esté disponible: compilar en el VPS, sin Trivy.

```bash
SHA=<SHA>
SRC=/opt/vito/src/ViTo-$SHA
B=/opt/vito/releases/$SHA

# Código del release (el repo es público)
mkdir -p /opt/vito/src
curl -fsSL "https://github.com/AppViTo/ViTo/archive/$SHA.tar.gz" | tar -xz -C /opt/vito/src

# Bundle: los mismos archivos que sube cd.yml (paso "Subir el bundle")
mkdir -p "$B"
cd "$SRC"
cp --parents docker-compose.prod.yml VERSION "$B"
for d in infrastructure/caddy infrastructure/docker/srs infrastructure/deploy/remote infrastructure/observability; do
  [ -d "$d" ] && cp -r --parents "$d" "$B"
done

# Versiones para compilar: todas las imágenes con el tag <SHA>
{
  for v in API_GATEWAY AUTH_SERVICE SOCIAL_SERVICE EVENT_SERVICE CHECKIN_SERVICE MOSAIC_SERVICE \
           STREAMING_SERVICE ARCHIVE_SERVICE CHAT_SERVICE MEDIA_SERVICE TRANSCODE_SERVICE \
           MODERATION_SERVICE MONGO_BACKUP; do
    echo "TAG_$v=$SHA"
  done
  echo CFG_CADDY=build
  echo CFG_SRS=build
} > /opt/vito/build.env

# Compilar de a uno: en paralelo, diez builds de Maven no caben en la RAM del VPS
for s in api-gateway auth-service social-service event-service checkin-service mosaic-service \
         streaming-service archive-service chat-service media-service transcode-service \
         moderation-service mongo-backup; do
  docker compose -p vito-build --project-directory "$SRC" -f "$SRC/docker-compose.prod.yml" \
    --env-file /opt/vito/.env --env-file /opt/vito/build.env build "$s" || { echo "FALLÓ $s"; break; }
done
docker image ls 'vito/*'      # 13 imágenes con el tag <SHA>
```

Estas imágenes no pasan por Trivy (en CI sí). Quedan reemplazadas a medida que lleguen
releases normales.

### B.5 Levantar

```bash
/opt/vito/releases/$SHA/infrastructure/deploy/remote/deploy.sh apply "$SHA"
```

Sin despliegue previo, `deploy.sh` usa `<SHA>` para todos los servicios, comprueba que
estén las 13 imágenes, crea `current` y espera la salud (hasta 5 min). Código `0` = listo;
otro código: tabla de [despliegue.md](despliegue.md#qué-hacer-con-cada-código). Mongo
arranca vacío.

### B.6 Datos

Con el stack arriba, restaurar el último backup como en el escenario A (pasos 3 a 5; el
paso 2 no hace falta porque la base está vacía):

```bash
dc stop api-gateway
dc exec -T mongo-backup entrypoint.sh restore --yes latest
dc start api-gateway
```

### B.7 Comprobar y cerrar

- `deploy.sh status`: todos *healthy*. Health por servicio: [incident.md, 2](incident.md#2-salud-por-servicio).
- Desde fuera: `curl -sI https://api.<DOMAIN>/` responde algo distinto de `502`/`503`
  (Caddy y gateway arriba); certificado válido.
- *ViTo CD* → *Run workflow* → `mode=preflight`: confirma huella SSH, secretos y
  configuración nuevos ([despliegue.md](despliegue.md#verificación-previa-sin-desplegar)).
- Borrar lo temporal: `rm -rf /opt/vito/src /opt/vito/build.env` y `docker builder prune -f`.
- El historial de rollback empieza de cero: `rollback.yml` solo puede volver a este
  release hasta que haya despliegues nuevos.
- El servidor viejo, si sigue vivo: apagarlo (no solo quitarle el DNS), porque su
  `mongo-backup` seguiría subiendo backups al mismo bucket.
- Anotar los tiempos de cada paso en el registro del incidente.

---

## C. Pruebas y ensayo

### C.1 Prueba mensual de restauración (ADR-011)

Restaura el último backup en un Mongo **temporal** del mismo VPS, sin tocar el de
producción. Fuera de un evento: usa ~1 GB de RAM extra.

```bash
TMP_PW=$(openssl rand -hex 24)
docker run -d --name mongo-restore-test --network vito_vito --memory 1g \
  -e MONGO_INITDB_ROOT_USERNAME=restore -e MONGO_INITDB_ROOT_PASSWORD="$TMP_PW" \
  mongo:7.0.14 --wiredTigerCacheSizeGB 0.25
sleep 20   # que termine de inicializar
dc exec -T mongo-backup entrypoint.sh restore --yes latest \
  --uri "mongodb://restore:$TMP_PW@mongo-restore-test:27017/?authSource=admin"
docker exec mongo-restore-test mongosh --quiet -u restore -p "$TMP_PW" --authenticationDatabase admin \
  --eval 'db.adminCommand({listDatabases: 1}).databases.forEach(d => print(d.name, d.sizeOnDisk))'
docker rm -f mongo-restore-test
```

Pasa si `restore` termina en `OK restaurado …` y aparecen las bases de los servicios con
tamaño distinto de cero. Anotar fecha, backup usado y duración.

### C.2 Ensayo de T-I14 (RTO)

El cierre de T-I14 es un ensayo completo del escenario B en una **máquina limpia**, con los
tiempos medidos. Para no afectar producción:

- **No tocar el DNS** ni los Environments. Sin DNS, Caddy no obtiene certificados: es
  esperado; se comprueba por dentro con [incident.md, 2](incident.md#2-salud-por-servicio).
- `SRS_CANDIDATE` = IP de la máquina de ensayo, en la copia del `.env` que se sube.
- Nada más terminar B.6, `dc stop mongo-backup`: con el `.env` de producción subiría
  backups al bucket real a las 03:00 UTC.
- Al terminar, **destruir la máquina**: tiene los secretos de producción.

| Paso | Estimado | Medido |
|------|---------:|-------:|
| B.1 VPS nuevo + `bootstrap.sh` | 30–60 min | |
| B.2 DNS y secretos (en un caso real) | 15 min | — |
| B.3 `.env` y SHA | 10 min | |
| B.4 Compilar 13 imágenes | 30–60 min | |
| B.5 `deploy.sh apply` | 5–10 min | |
| B.6 Restaurar Mongo | 5–30 min | |
| B.7 Comprobaciones | 15 min | |
| **Total (objetivo ≤ 4 h)** | **~2–3 h** | |

Registrar el resultado (fecha, SHA, tamaño del backup, tiempos y problemas) en el PR o issue
que cierre el ensayo, sin datos sensibles.
