# Observabilidad de producción (T-I13)

Alloy corre en el VPS junto a Compose y envía métricas, logs y trazas a Grafana Cloud.
No publica puertos: el receptor OTLP (`4317/4318`) solo es alcanzable desde la red
`vito`. El socket de Docker solo lo ve `docker-socket-proxy`, en solo lectura.

Contrato vinculante: [`specs/001-cd-produccion/contracts/observability.md`](../../specs/001-cd-produccion/contracts/observability.md).

## Variables de entorno

Se cargan desde el secreto `DOTENV_PRODUCTION` (nunca se versionan):

| Variable | Ejemplo | Uso |
|----------|---------|-----|
| `GRAFANA_CLOUD_PROM_URL` | `https://prometheus-prod-<region>.grafana.net/api/prom/push` | remote_write de métricas |
| `GRAFANA_CLOUD_PROM_USER` | `123456` | usuario numérico del stack |
| `GRAFANA_CLOUD_LOKI_URL` | `https://logs-prod-<region>.grafana.net/loki/api/v1/push` | envío de logs |
| `GRAFANA_CLOUD_LOKI_USER` | `123456` | usuario numérico del stack |
| `GRAFANA_CLOUD_OTLP_URL` | `https://otlp-gateway-prod-<region>.grafana.net/otlp` | trazas OTLP |
| `GRAFANA_CLOUD_OTLP_USER` | `123456` | usuario numérico del stack |
| `GRAFANA_CLOUD_TOKEN` | `glc_...` | access policy token |

El token necesita los scopes `metrics:write`, `logs:write` y `traces:write`. No uses una
API key personal ni pegues el token en el repositorio, en un issue o en un log.

## Puesta en marcha en Grafana Cloud

1. Crea (o localiza) el stack y copia las URLs y el usuario numérico de Prometheus, Loki
   y OTLP.
2. Crea una access policy con los tres scopes y genera un token. Guárdalo directamente en
   `DOTENV_PRODUCTION`.
3. Despliega con el workflow normal. Alloy arranca con `depends_on: docker-socket-proxy`.
4. Comprueba que Alloy no reinicia y que no hay errores de autenticación:
   `dc logs --tail=100 alloy` (la función `dc` está en
   [incident.md](../../docs/runbooks/incident.md)).
5. En Grafana Cloud, confirma que llegan las series `up{job="vito"}` y los logs con
   etiquetas `service` y `level` (`{cluster="vito-prod"}` en Explore). Si llegan métricas
   pero no logs, busca en `dc logs alloy` un `403 Forbidden` de `discovery.docker`: el
   proxy no deja leer algún endpoint (por ejemplo `/networks`).

## Contact point `leads`

En **Alerting → Contact points → New contact point**, crea `leads` con:

- Email del equipo y email de cada lead.
- Telegram: bot token y chat ID configurados **en Grafana**, no en el repo.

Envía una notificación de prueba y confirma que llega a ambos canales. Después, en
**Alerting → Notification policies → Default policy → Edit**, elige `leads` como contact
point por defecto y comprueba que la política diga *Delivered to leads* y que `leads` ya no
aparezca como *Unused*. La notificación de prueba no pasa por la política: si se omite este
paso, las reglas se disparan pero los avisos van al contact point vacío del stack (pasó en
la prueba de T041).

## Alertas

La plantilla está en [`alerts/vito-alerts.yaml`](alerts/vito-alerts.yaml). La interfaz de
Grafana Cloud no importa ese formato; [`alerts/apply_alerts.py`](alerts/apply_alerts.py) la
aplica por la API (solo biblioteca estándar de Python). Necesita un token de una service
account con rol Admin, que se borra al terminar:

```powershell
cd infrastructure/observability/alerts
docker run --rm -v "${PWD}:/a" mikefarah/yq:4.44.3 -o=json . /a/vito-alerts.yaml > vito-alerts.json
$env:GRAFANA_URL = "https://<stack>.grafana.net"
$env:GRAFANA_TOKEN = "<token de la service account>"
python apply_alerts.py --dry-run     # no cambia nada
python apply_alerts.py               # crea o actualiza el grupo vito-produccion
Remove-Item Env:GRAFANA_TOKEN, vito-alerts.json
```

El script resuelve los UIDs de las fuentes `grafanacloud-*-prom` y `grafanacloud-*-logs`,
reutiliza o crea la carpeta `ViTo` y deja las reglas editables en la interfaz. Omite
`backup-missing` salvo con `--with-backup`: sin logs en Loki se dispararía de inmediato.

| Alerta | Condición | Durante |
|--------|-----------|---------|
| `api-down` | `probe_success == 0` en las 2 sondas | 2 min |
| `service-unhealthy` | `up{job="vito"} == 0` por servicio | 5 min |
| `host-mem-high` | memoria usada > 80 % | 10 min |
| `gateway-401-spike` | > 5 respuestas 401/s en el gateway | 5 min |
| `gateway-429-spike` | > 5 respuestas 429/s en el gateway | 5 min |
| `gateway-revocation-errors` | incrementa `vito.gateway.revocation.check.errors` | 5 min |
| `backup-missing` | ninguna línea `OK ` de `service="mongo-backup"` | 26 h |

`api-down` es la alerta de guardia: 2 minutos de detección + entrega < 1 minuto, dentro
del objetivo de 5 minutos de SC-005. `service-unhealthy` y las de host son avisos previos.

## Synthetic Monitoring

Crea un check HTTP:

- URL: `https://api.<dominio>/healthz`. Con el subdominio `api.`: sin él, la petición llega
  al frontend de Cloudflare Pages, que responde `200` en cualquier ruta, y el check nunca
  falla (pasó en la prueba de T041). Comprobación: la URL debe devolver el JSON de la
  readiness del gateway, no HTML.
- Método: `GET`
- Frecuencia: `1m`
- Sondas: 2 (regiones distintas)
- Condición: status `200`
- Label `job=vito-api-healthz` (la expresión de `api-down` usa ese label)

El endpoint `/healthz` es infraestructura: Caddy lo reescribe a
`/actuator/health/readiness` del gateway y no debe confundirse con la API pública.

## Tableros

Importa en **Dashboards → New → Import**:

- [`dashboards/vito-api-red.json`](dashboards/vito-api-red.json): peticiones/min, % 4xx/5xx,
  p95/p99 por URI del gateway, `up` y memoria JVM por servicio, y 429 por ruta de los enlaces
  de invitación (Ola 1, T062).
- [`dashboards/vito-services-red.json`](dashboards/vito-services-red.json): `up`, memoria,
  CPU y reinicios de contenedor por servicio.

Ambos usan la variable `datasource`; selecciona tu datasource de Prometheus después de
importar. No se hardcodean UIDs de datasource.

## Audit log de seguridad (T-B24)

`auth-service` escribe una línea JSON por evento de cuenta en el logger `AUDIT`. Los campos son:

- `event`: `register`, `login.success`, `login.failure`, `logout` o `refresh.rejected`.
- `userId`: no aparece en un login con un correo inexistente ni en un refresh ilegible.
- `ip`: truncada a /24 en IPv4 y a /48 en IPv6.
- `traceId`.

Nunca se registran el correo, la contraseña ni los tokens. En **Explore** (Loki):

```logql
# Todos los eventos de las últimas horas
{service="auth-service"} | json | logger_name="AUDIT"

# Logins fallidos por red /24 en ventanas de 5 min (fuerza bruta, relleno de credenciales)
sum by (ip) (count_over_time({service="auth-service"} | json | logger_name="AUDIT" | event="login.failure" [5m]))

# Historial de una cuenta
{service="auth-service"} | json | logger_name="AUDIT" | userId="<id>"
```

## cAdvisor: límites y fallback

`prometheus.exporter.cadvisor` habla con `docker-socket-proxy` en solo lectura. Si los
paneles de contenedor salen vacíos:

1. Revisa que el proxy tenga `CONTAINERS=1`, `EVENTS=1`, `INFO=1`, `NETWORKS=1`,
   `VERSION=1` y `PING=1`, y que el socket esté montado únicamente en el proxy.
2. Revisa `docker compose logs alloy` y busca errores de conexión a
   `tcp://docker-socket-proxy:2375`.
3. Si el kernel o la versión de Docker no permiten las métricas necesarias, el fallback
   es quitar los paneles de cAdvisor y usar `up`, JVM y métricas del host. No añadas
   `privileged: true` al contenedor Alloy sin revisión de seguridad y sin actualizar el
   contrato.

## Free tier

El plan gratuito de Grafana Cloud limita series, logs, trazas, retención y usuarios.
Alloy muestrea trazas al 10 % (`TRACING_SAMPLE_RATE=0.1`) y solo conserva la lista blanca
de métricas del contrato. Si se supera el free tier, primero baja retención o muestreo;
no amplíes el plan sin decisión explícita.

## Validación local

```bash
# Requiere Docker; en WSL sin Docker usa ALLOY_BIN con el binario de Alloy.
docker run --rm -v "$PWD/infrastructure/observability:/etc/alloy:ro" \
  -e GRAFANA_CLOUD_PROM_URL=https://example.test/api/prom/push \
  -e GRAFANA_CLOUD_PROM_USER=1 \
  -e GRAFANA_CLOUD_LOKI_URL=https://example.test/loki/api/v1/push \
  -e GRAFANA_CLOUD_LOKI_USER=1 \
  -e GRAFANA_CLOUD_OTLP_URL=https://example.test/otlp \
  -e GRAFANA_CLOUD_OTLP_USER=1 \
  -e GRAFANA_CLOUD_TOKEN=dummy \
  grafana/alloy:v1.21.3 validate /etc/alloy/config.alloy

bash infrastructure/observability/tests/masking.sh
```

CI ejecuta `alloy fmt --test`, `alloy validate` y el test de enmascarado en el job
`Deploy Scripts`. El test cubre `Bearer`, `?token=`, `"password"`, JWT de QR y correos.
