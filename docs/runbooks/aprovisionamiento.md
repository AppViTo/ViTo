# Runbook · Aprovisionamiento de producción (T-I06)

Guía para dejar listo todo lo que necesita el despliegue continuo
([feature 001](../../specs/001-cd-produccion/spec.md), FR-013 a FR-017). La ejecuta un lead
(ADR-013): el agente no tiene acceso SSH ni ve secretos.

> **El repo es público.** Ningún valor real de esta guía (IP, dominio, correos, IDs de
> cuenta, llaves, tokens) se escribe en el repo, en un PR, en un issue ni en un chat con un
> agente. Viven en el gestor de contraseñas, en los GitHub Environments y en el VPS. En los
> comandos, `<host>` es la IP del VPS y `<dominio>`, el dominio raíz.

Orden: los pasos 1 y 2 van primero (sin SSH no hay nada). Del 5 al 8 pueden ir en
cualquier orden, pero el 4 necesita sus resultados. Al final, el paso 11 lo comprueba todo.

| # | Paso | Resultado que se guarda en el gestor |
|---|------|--------------------------------------|
| 1 | Llave del pipeline y huella del servidor | `SSH_KEY`, `SSH_KNOWN_HOSTS` |
| 2 | `bootstrap.sh` | — |
| 3 | Environments de GitHub | — |
| 4 | Secretos y variables | `DOTENV_PRODUCTION`, `MONGO_DEPLOY_RO_PASSWORD` |
| 5 | DNS en Cloudflare | — |
| 6 | Proyecto de Pages | `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` |
| 7 | Resend | `RESEND_API_KEY` |
| 8 | Bot y grupo de Telegram | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` |
| 9 | Seguridad del repo | — |
| 10 | Inventario de ADR-013 | — |
| 11 | Verificación | — |

---

## 1. Llave del pipeline y huella del servidor

La llave del pipeline es **solo para el VPS** y no lleva frase de paso (la usa GitHub
Actions). La llave personal de cada lead es aparte y sí lleva frase de paso.

```bash
# En la máquina del lead, en un directorio temporal
ssh-keygen -t ed25519 -C "vito-pipeline" -N "" -f vito-pipeline
```

- `vito-pipeline` (privada) → gestor y, en el paso 4, el secreto `SSH_KEY`. Después se borra
  de la máquina.
- `vito-pipeline.pub` → `--pubkey` de `bootstrap.sh` en el paso 2.

**Huella del servidor (`SSH_KNOWN_HOSTS`, FR-014).** Hay que compararla con la del propio
servidor, porque la primera conexión por red podría estar interceptada:

```bash
# 1) Desde la consola VNC de Contabo (no por SSH), en el VPS:
ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub

# 2) Desde la máquina del lead:
ssh-keyscan -t ed25519 <host> > known_hosts.vito
ssh-keygen -lf known_hosts.vito
```

Las dos huellas `SHA256:…` deben ser idénticas. Si no lo son, **parar** y avisar al otro
lead. El contenido de `known_hosts.vito` es el secreto `SSH_KNOWN_HOSTS`.

## 2. Preparar el servidor con `bootstrap.sh`

[`infrastructure/vps/bootstrap.sh`](../../infrastructure/vps/bootstrap.sh) instala Docker y
Compose, `zstd`, `ufw`, `fail2ban` y actualizaciones desatendidas. También crea el usuario
`deploy` y `/opt/vito`, desactiva el login con contraseña y abre solo los puertos de
producción (research R7). Es idempotente: se puede correr de nuevo.

1. **Antes de nada, instalar la llave personal en root.** El script desactiva las
   contraseñas de SSH y aborta si root no tiene `authorized_keys`, para no dejar como única
   entrada la consola de Contabo.

   ```bash
   ssh-copy-id root@<host>
   ssh root@<host> true    # debe entrar sin pedir contraseña
   ```

2. Confirmar el sistema: `ssh root@<host> 'grep PRETTY_NAME /etc/os-release'`. Se espera
   Ubuntu 24.04 (o 22.04). Con otro sistema el script sale con código 3 (R16).

3. Copiar y correr el script. Los `--pubkey` son **la lista completa** de llaves de
   `deploy`: la del pipeline y la de cada lead. Una llave que no se pasa se quita (así se
   revoca un acceso, ADR-013 punto 7).

   ```bash
   scp infrastructure/vps/bootstrap.sh root@<host>:/root/
   ssh root@<host> bash /root/bootstrap.sh \
     --pubkey "'$(cat vito-pipeline.pub)'" \
     --pubkey "'$(cat ~/.ssh/id_ed25519.pub)'" \
     --pubkey "'<llave pública del otro lead>'"
   ```

4. El script termina con un chequeo. Todas las líneas deben decir `[OK]`. Para repetirlo
   después sin cambiar nada: `ssh root@<host> bash /root/bootstrap.sh --check-only`.

5. Comprobar desde fuera:

   ```bash
   ssh -i vito-pipeline deploy@<host> docker ps            # entra y responde
   ssh -o PubkeyAuthentication=no deploy@<host>            # rechazado: sin contraseñas
   ```

**Puertos.** `ufw` deja `22/tcp` limitado por tasa y abiertos `80/tcp`, `443/tcp`,
`443/udp` (HTTP/3), `1935/tcp` (RTMP) y `8000/udp` (WebRTC). El 22 no se puede filtrar por
IP porque las de los runners de GitHub cambian (R7). **Ojo:** Docker publica los `ports:`
del compose por fuera de `ufw`. Mongo, Redis y Kafka quedan aislados porque no tienen
`ports:` en `docker-compose.prod.yml`; un `ports:` nuevo en el compose queda expuesto a
internet aunque `ufw` no lo permita.

## 3. Environments de GitHub

Settings → Environments → *New environment*, tres veces (contrato:
[workflows.md](../../specs/001-cd-produccion/contracts/workflows.md#environments)):

| Environment | Required reviewers | Prevent self-review | Deployment branches |
|-------------|--------------------|:-------------------:|---------------------|
| `production` | los dos leads | ✅ | *Selected branches*: `main` |
| `production-hotfix` | los dos leads | ❌ | *Selected branches*: `main` |
| `production-preflight` | ninguno | — | *Selected branches*: `main` |

- `production`: con *Prevent self-review*, quien dispara el despliegue no lo puede aprobar;
  lo aprueba el otro lead (constitución v1.3.0, Principio VIII).
- `production-hotfix`: sin esa opción, para que un lead solo pueda sacar un hotfix si el
  otro no está disponible (R5).
- `production-preflight`: solo lo usa la verificación previa (`cd.yml`, `mode=preflight`),
  que no despliega nada.

## 4. Secretos y variables

Los **tres** Environments llevan los mismos secretos y variables. Cargarlos con `gh` desde
archivos o desde la entrada estándar, nunca con `--body` (quedaría en el historial de la
terminal):

```bash
for env in production production-hotfix production-preflight; do
  gh secret set SSH_KEY         --env "$env" < vito-pipeline
  gh secret set SSH_KNOWN_HOSTS --env "$env" < known_hosts.vito
  gh secret set SSH_HOST        --env "$env"      # pega el valor y Ctrl-D
  # … y así con cada secreto de la tabla
done
```

**Secretos**

| Nombre | Valor | De dónde sale |
|--------|-------|---------------|
| `SSH_HOST` | IP del VPS | panel de Contabo |
| `SSH_USER` | `deploy` | paso 2 |
| `SSH_KEY` | llave privada `vito-pipeline` | paso 1 |
| `SSH_KNOWN_HOSTS` | contenido de `known_hosts.vito` | paso 1 |
| `DOTENV_PRODUCTION` | `.env` de producción completo | ver abajo |
| `MONGO_DEPLOY_RO_PASSWORD` | contraseña aleatoria del usuario de solo lectura `vito_deploy_ro` (R6) | `openssl rand -base64 48 \| tr -d '/+=' \| cut -c1-48` |
| `CLOUDFLARE_API_TOKEN` | token con *Cloudflare Pages: Edit* | paso 6 |
| `CLOUDFLARE_ACCOUNT_ID` | ID de la cuenta (secreto porque el repo es público) | paso 6 |
| `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` | bot y grupo de avisos | paso 8 |
| `RESEND_API_KEY` | llave de envío | paso 7 |
| `ALERT_EMAILS` | correos de los dos leads, separados por coma | — |

**`DOTENV_PRODUCTION`** (R15). Se arma a partir de
[`.env.prod.example`](../../.env.prod.example), con una copia en el gestor. El pipeline lo
escribe en `/opt/vito/.env` (`0600`) en cada despliegue: para cambiar un valor se edita el
secreto y se redespliega, sin entrar por SSH.

- Los secretos aleatorios (`MONGO_ROOT_PASSWORD`, `REDIS_PASSWORD`, `KAFKA_PASSWORD`,
  `JWT_SECRET`, `QR_SECRET`) se generan con el `openssl` de la tabla, cada uno distinto.
- `KAFKA_CLUSTER_ID` se genera una vez (comando en el ejemplo) y no cambia nunca.
- `DOMAIN` es el dominio raíz; `ACME_EMAIL`, el correo de equipo; `CORS_ALLOWED_ORIGINS`,
  `https://app.<dominio>`; `SRS_CANDIDATE`, la IP del VPS.
- `S3_*`: el endpoint de R2 y el token *Object Read & Write* limitado a los buckets
  `vito-media` y `vito-backups`. `S3_PUBLIC_URL` es `https://media.<dominio>` (paso 5).
- `IMAGE_TAG` no va: desde la fase B de la feature 001 las versiones llegan por
  `versions.env` (R1).
- `GRAFANA_CLOUD_*` se agregan en la fase E (T-I13); hasta entonces no hacen falta.

**Variables** (Settings → Environments → *Environment variables*; no son secretas):

| Nombre | Valor |
|--------|-------|
| `VITO_API_BASE_URL` | `https://api.<dominio>/api/v1` |
| `VITO_WS_BASE_URL` | `wss://api.<dominio>` |
| `PAGES_PROJECT` | `vito-web` |

Al terminar, borrar de la máquina `vito-pipeline`, `vito-pipeline.pub` y
`known_hosts.vito`: ya están en el gestor.

## 5. DNS en Cloudflare

En la zona del dominio (DNS → Records):

| Nombre | Tipo | Destino | Proxy |
|--------|------|---------|-------|
| `api` | A | IP del VPS | **DNS only** (gris) |
| `live` | A | IP del VPS | **DNS only** (gris) |
| `media` | — | dominio propio del bucket R2 `vito-media` | lo crea R2 |
| `app` | — | dominio propio del proyecto de Pages | lo crea Pages (paso 6) |

- `api` y `live` van sin el proxy de Cloudflare. RTMP y WebRTC no pasan por el proxy; en
  `api`, Caddy saca y renueva sus certificados de Let's Encrypt sin intermediarios. La IP
  igual es pública por `live` y por el candidato WebRTC de SRS.
- `media`: en R2 → `vito-media` → Settings → *Custom Domains* → *Connect domain* →
  `media.<dominio>`. Cloudflare crea el registro. No apunta al VPS: los archivos se sirven
  directo desde R2.

Comprobar: `dig +short api.<dominio>` y `dig +short live.<dominio>` devuelven la IP del
VPS.

## 6. Proyecto de Cloudflare Pages

La web se compila en GitHub Actions y se sube con *Direct Upload* (`wrangler`). **No** se
conecta el repo con la integración Git de Pages, porque se saltaría CI y la aprobación del
Environment (R9; ADR-011, actualización del 2026-10-22).

1. Workers & Pages → *Create* → Pages → *Direct Upload* → nombre `vito-web`, rama de
   producción `main`. Si pide un primer archivo, sirve cualquier `index.html` provisional.
2. `vito-web` → *Custom domains* → `app.<dominio>`.
3. **Token:** My Profile → API Tokens → *Create Token* → *Custom token*. Permiso
   *Account → Cloudflare Pages → Edit*, limitado a la cuenta de ViTo, sin otros permisos.
   → `CLOUDFLARE_API_TOKEN`.
4. **ID de la cuenta:** en el panel derecho de *Workers & Pages* → `CLOUDFLARE_ACCOUNT_ID`.

## 7. Resend (FR-017)

1. Resend → Domains → *Add domain* → `send.<dominio>` (R12).
2. Resend muestra los registros: un MX y un TXT de SPF para la ruta de retorno y un TXT de
   DKIM (`resend._domainkey…`). Cargarlos en Cloudflare **con el nombre exacto que muestra
   Resend**, con el proxy apagado.
3. Agregar además el DMARC del dominio raíz, que empieza sin bloquear:

   | Nombre | Tipo | Valor |
   |--------|------|-------|
   | `_dmarc` | TXT | `v=DMARC1; p=none` |

4. *Verify* en Resend. Puede tardar unos minutos.
5. API Keys → *Create API key* → permiso *Sending access*, limitado al dominio
   `send.<dominio>` → `RESEND_API_KEY`.

El MX de `send.` no choca con Cloudflare Email Routing (`infra@<dominio>`, ADR-013), que
usa el MX del dominio raíz. Plan gratuito: 3.000 correos al mes y 100 al día.

## 8. Bot y grupo de Telegram

1. En Telegram, `@BotFather` → `/newbot` → nombre `ViTo alertas` → token →
   `TELEGRAM_BOT_TOKEN`.
2. Crear un grupo con los dos leads y agregar el bot.
3. Escribir en el grupo `/start@<nombre_del_bot>`, y luego obtener el `chat_id` (un número
   negativo) sin dejar el token en el historial:

   ```bash
   read -rs TOKEN   # pega el token
   curl -s "https://api.telegram.org/bot${TOKEN}/getUpdates" | grep -o '"chat":{"id":-[0-9]*'
   ```

   → `TELEGRAM_CHAT_ID`.
4. Prueba: `curl -s "https://api.telegram.org/bot${TOKEN}/sendMessage" -d chat_id=<id> -d text=prueba`
   tiene que llegar al grupo.

## 9. Seguridad del repo

Settings → *Advanced Security* (R13, R18):

- **Secret Protection:** activar *Secret scanning* y *Push protection*.
- **Dependabot alerts:** activar. Las actualizaciones las propone `.github/dependabot.yml`
  (T050).

## 10. Inventario de ADR-013

Actualizar la tabla *Estado del inventario* de
[ADR-013](../decisions/adr-013-account-ownership.md) (sección *Actualizaciones*) con lo que
quedó listo, y el avance de T-I06 en [`docs/roadmap/tasks.md`](../roadmap/tasks.md). Solo
estados: sin correos, IDs de cuenta, IP ni dominio.

## 11. Verificación

- En el VPS: `bootstrap.sh --check-only` con todo en `[OK]` (paso 2).
- `dig` de `api.` y `live.` (paso 5); `https://media.<dominio>` responde desde R2.
- Resend muestra el dominio *Verified*; el mensaje de prueba de Telegram llegó.
- Cuando el pipeline esté en `main` (fase C): correr `cd.yml` con `mode=preflight`
  ([quickstart, paso 0](../../specs/001-cd-produccion/quickstart.md#0-verificación-previa-historia-5)).
  Comprueba la huella SSH, el `.env` real con `docker compose config -q` y que estén todos
  los secretos, sin desplegar nada (T026).
