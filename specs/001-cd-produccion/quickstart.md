# Quickstart: validar el despliegue continuo

Guía para comprobar de punta a punta que la feature funciona. Los pasos de aprovisionamiento
detallados quedan en `docs/runbooks/aprovisionamiento.md` (se crea en las tareas). Aquí solo
va lo que demuestra cada historia.

## Prerrequisitos

- `bootstrap.sh` ya corrió en el VPS ([contrato](./contracts/remote-scripts.md#bootstrapsh-fuera-del-bundle-infrastructurevpsbootstrapsh)).
- Environments `production` y `production-hotfix` creados, con todos los secretos y
  variables de [workflows.md](./contracts/workflows.md#secretos-y-variables-por-environment-los-dos-environments-llevan-los-mismos).
- DNS en Cloudflare: `api.`, `live.` y `media.` apuntan al VPS; la web tiene su dominio en
  Pages.
- Grafana Cloud: contact point `leads` (correo + Telegram), tablero y alertas importados.

## 0. Verificación previa (Historia 5)

Ejecutar `cd.yml` con `workflow_dispatch` en modo verificación (paso `preflight`):
- **Esperado:** SSH con huella fija OK, `docker compose config -q` OK con el `.env` real,
  y los secretos requeridos presentes. No reinicia nada.
- **Prueba negativa:** quitar un secreto (por ejemplo `TELEGRAM_CHAT_ID`) → falla nombrando
  solo ese secreto.

## 1. Primer despliegue (Historia 1)

PR de release extraordinario `develop` → `main` (aclaración 4). Aprueba el otro lead.
- **Esperado:** se construyen los 13 servicios, `deploy.sh status` muestra todos con el mismo
  SHA y la salud OK, `https://api.<dominio>/healthz` responde 200 y el resumen del job
  muestra aprobador y versiones.

## 2. Despliegue de un solo servicio (SC-001, SC-002)

Release con un cambio trivial en `backend/event-service/`.
- **Esperado:** solo `event-service` aparece en `changed`. `docker ps` muestra que los demás
  contenedores conservan su `CreatedAt`. De la aprobación a "sano" pasan ≤ 15 min.
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
  en `deployments.log`.

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
- Buscar `Bearer` en Loki → solo aparece enmascarado.

## 8. Secretos (SC-007)

- Secret scanning y push protection activos en el repo.
- Revisar los logs de los runs de los pasos 1 a 6: ninguna IP, llave ni valor del `.env` en
  claro.
