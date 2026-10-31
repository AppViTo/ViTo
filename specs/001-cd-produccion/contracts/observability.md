# Contrato: señales, tablero y alertas

## Señales que salen del VPS (Alloy → Grafana Cloud)

| Señal | Origen | Etiquetas permitidas | Filtro |
|-------|--------|----------------------|--------|
| Métricas de servicio | `/actuator/prometheus` de los 10 Java | `service`, `job`, `uri`, `method`, `status`, `outcome` | lista blanca: `http_server_requests_seconds_*`, `jvm_memory_used_bytes`, `jvm_gc_pause_seconds_*`, `process_cpu_usage`, `hikaricp_*`/`mongodb_driver_*` si existen, `vito_gateway_*`, `up` |
| Métricas de host | exportador unix de Alloy | `service`, `instance` | CPU, memoria, disco, red |
| Métricas de contenedor | cAdvisor de Alloy | `service` (nombre en Compose) | memoria, CPU, reinicios |
| Logs | stdout/stderr de todos los contenedores | `service`, `level` | enmascarado de `Bearer …`, `token=…`, `"password":…`, JWT sueltos (`eyJ[\w-]+\.[\w-]+\.[\w-]+`, cubre los QR) y correos (FR-026) |
| Trazas | OTLP desde los servicios Java | `service.name` | muestreo del 10 % |
| Uptime | Synthetic Monitoring | — | `GET https://api.<dominio>/healthz` cada 1 min desde 2 sondas |

## Tablero `vito-api-red` (FR-024)

Peticiones por minuto, % de 5xx y 4xx, y latencia p95/p99 por `uri` en el gateway, más un
panel por servicio con su estado (`up`) y su memoria contra el `mem_limit`.

## Alertas (FR-025)

Todas notifican al contact point `leads` (correo de equipo, correo de cada lead y Telegram).

| Nombre | Fuente | Condición | Durante |
|--------|--------|-----------|---------|
| `api-down` | Synthetic | `probe_success == 0` en las 2 sondas | 2 min |
| `service-unhealthy` | métricas | `up{job="vito"} == 0` por servicio | 5 min |
| `host-mem-high` | métricas de host | uso > 80 % | 10 min |
| `host-disk-high` | pendiente (T-I13): sin montaje de `/` del host | — | — |
| `gateway-401-spike` | métricas | `sum(rate(http_server_requests_seconds_count{service="api-gateway",status="401"}[5m])) > 5` | 5 min |
| `gateway-429-spike` | métricas | `sum(rate(http_server_requests_seconds_count{service="api-gateway",status="429"}[5m])) > 5` | 5 min |
| `gateway-revocation-errors` | métricas | `sum(rate(vito_gateway_revocation_check_errors_total[5m])) > 0` | 5 min |
| `backup-missing` | logs | ninguna línea `OK ` de `service="mongo-backup"` | 26 h |

**SC-005:** `api-down` (2 min) más la entrega (< 1 min) queda dentro de los 5 minutos.
