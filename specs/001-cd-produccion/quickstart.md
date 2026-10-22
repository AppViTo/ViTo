# Quickstart: validar el despliegue continuo

Guía para comprobar de punta a punta que la feature funciona. Los pasos de aprovisionamiento
detallados quedan en `docs/runbooks/aprovisionamiento.md` (se crea en las tareas). Aquí solo
va lo que demuestra cada historia.

## Prerrequisitos

- `bootstrap.sh` ya corrió en el VPS ([contrato](./contracts/remote-scripts.md#bootstrapsh-fuera-del-bundle-infrastructurevpsbootstrapsh)).
- Environments `production`, `production-hotfix` y `production-preflight` creados, con todos
  los secretos y variables de [workflows.md](./contracts/workflows.md#secretos-y-variables-por-environment-los-tres-environments-llevan-los-mismos).
- `backend/SECURITY_CHECKLIST.md` sin `TODO` de severidad alta sin excepción (T051).
- DNS en Cloudflare: `api.` y `live.` apuntan al VPS; `media.` es el dominio del bucket R2; la web tiene su dominio en
  Pages.
- Grafana Cloud: contact point `leads` (correo + Telegram), tablero y alertas importados.

## 0. Verificación previa (Historia 5)

Ejecutar `cd.yml` con `workflow_dispatch` en modo verificación (paso `preflight`):
- **Esperado:** SSH con huella fija OK, `docker compose config -q` OK con el `.env` real,
  y los secretos requeridos presentes. No pide aprobación ni espera a un despliegue
  pendiente (`vps-preflight`). No reinicia nada.
- **Prueba negativa:** quitar un secreto (por ejemplo `TELEGRAM_CHAT_ID`) → falla nombrando
  solo ese secreto.

## 1. Primer despliegue (Historia 1)

PR de release extraordinario `develop` → `main` (aclaración 4). Aprueba el otro lead.
- **Esperado:** se construyen los 13 servicios, `deploy.sh status` muestra todos con el mismo
  SHA y la salud OK, `https://api.<dominio>/healthz` responde 200 y el resumen del job
  muestra aprobador y versiones. `live-check` aparece como omitido (`first-deploy`). Las
  imágenes tienen los tags `<sha>` y `v<VERSION>`, y existe el tag git `v<VERSION>`.

## 2. Despliegue de un solo servicio (SC-001, SC-002)

Release con un cambio trivial en `backend/event-service/`.
- **Esperado:** solo `event-service` aparece en `changed`. `docker ps` muestra que los demás
  contenedores conservan su `CreatedAt`. De la aprobación a "sano" pasan ≤ 15 min.
- **Configuración montada:** `docker inspect caddy` muestra el montaje del Caddyfile desde
  `/opt/vito/current/...` (Compose no resolvió el symlink). Un release que cambia solo el
  Caddyfile recrea Caddy y nada más.
- **Autoaprobación:** el lead que disparó el run intenta aprobar → GitHub no lo permite.

## 3. Vuelta automática (aclaración 2, SC-004)

Release con `event-service` roto a propósito (por ejemplo, una variable requerida
inexistente en su perfil).
- **Esperado:** `deploy.sh` sale con 5, `event-service` vuelve a la versión anterior y está
  sano en ≤ 10 min, y llega el aviso a Telegram y al correo.

## 4. Bloqueo por evento en vivo (FR-028)

Crear un evento `PUBLISHED` con inicio hace 10 min y fin en 2 h, y luego disparar un release.
- **Esperado:** el job falla en `live-check` (código 3) nombrando el evento, sin tocar
  servicios. Con un hotfix y `force_live_event=true` + `reason`, despliega y el motivo queda
  en `deployments.log`. Un rollback con `force_live_event=true` + `reason` también pasa; un
  release normal con `force_live_event=true` es rechazado.

## 5. Rollback (Historia 2, SC-003)

`rollback.yml` con el SHA del paso 1.
- **Esperado:** sin builds; todos los servicios con las versiones de ese release, sanos en
  ≤ 5 min. Con un SHA no retenido: falla con código 4 antes de tocar nada.

## 6. Web (Historia 3)

Cambio en `frontend/vito_app/` que entra a `main`.
- **Esperado:** se publica en Pages después de la aprobación. Al abrir directo
  `https://<web>/events/123`, carga la app (sin 404). Con `VITO_API_BASE_URL` vacía, el
  workflow falla antes de construir y la versión anterior sigue en línea.

## 7. Observabilidad (Historia 4, SC-005)

- `docker compose stop api-gateway` en el VPS → alerta `api-down` en Telegram y en el correo
  en ≤ 5 min; al volver a levantarlo, llega el aviso de resolución.
- El tablero `vito-api-red` muestra datos de la última hora.
- Buscar `Bearer` y `eyJ` en Loki → solo aparecen enmascarados.
- Los logs llegan en JSON, con `traceId` y `spanId`.
- A los 7 días, el uso de series, logs y trazas está dentro del plan gratuito (FR-027).

## 8. Secretos (SC-007)

- Secret scanning y push protection activos en el repo; `Secrets Scan` (gitleaks) en verde.
- Una imagen con una vulnerabilidad CRITICAL conocida y sin excepción hace fallar `build`.
- Revisar los logs de los runs de los pasos 1 a 6: ninguna IP, llave ni valor del `.env` en
  claro.
