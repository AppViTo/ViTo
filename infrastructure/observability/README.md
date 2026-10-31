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
   `docker compose --env-file .env.prod -f docker-compose.prod.yml logs --tail=100 alloy`.
5. En Grafana Cloud, confirma que llegan las series `up{job="vito"}` y los logs con
   etiquetas `service` y `level`.

## Contact point `leads`

En **Alerting → Contact points → New contact point**, crea `leads` con:

- Email del equipo y email de cada lead.
- Telegram: bot token y chat ID configurados **en Grafana**, no en el repo.

Envía una notificación de prueba y confirma que llega a ambos canales. Después asígnalo
como contact point por defecto de las reglas (o en la política de notificación).

## Alertas

La plantilla está en [`alerts/vito-alerts.yaml`](alerts/vito-alerts.yaml). Sustituye los
UIDs `grafanacloud-prom` y `grafanacloud-logs` por los de tu stack y aplícala con tu
método habitual (Terraform, `grafana provisioning` o creándolas en la UI con las mismas
expresiones).

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

- URL: `https://api.<dominio>/healthz`
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
  p95/p99 por URI del gateway, `up` y memoria JVM por servicio.
- [`dashboards/vito-services-red.json`](dashboards/vito-services-red.json): `up`, memoria,
  CPU y reinicios de contenedor por servicio.

Ambos usan la variable `datasource`; selecciona tu datasource de Prometheus después de
importar. No se hardcodean UIDs de datasource.

## cAdvisor: límites y fallback

`prometheus.exporter.cadvisor` habla con `docker-socket-proxy` en solo lectura. Si los
paneles de contenedor salen vacíos:

1. Revisa que el proxy tenga `CONTAINERS=1`, `EVENTS=1`, `INFO=1`, `VERSION=1` y
   `PING=1`, y que el socket esté montado únicamente en el proxy.
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
