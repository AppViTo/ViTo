# infrastructure/deploy — despliegue continuo al VPS

Feature [001](../../specs/001-cd-produccion/plan.md) (Fase Tracción, ADR-011). Sin
registro de imágenes ni orquestador: GitHub Actions construye, copia y aplica por SSH.

```
merge a main ─► cd.yml: changes ─► build ─► (aprobación del Environment) ─► deploy
                  │                  │                                        │
     changed-services.sh       docker save | zstd             ssh deploy@vps: docker load,
     (qué servicios)           (artifact por servicio)        bundle → releases/<sha>/,
                                                              remote/deploy.sh apply <sha>
```

| Ruta | Dónde corre | Qué hace |
|------|-------------|----------|
| `changed-services.sh` | runner | rutas cambiadas desde el último despliegue exitoso → servicios a reconstruir (research R2) |
| `pick-environment.sh` | runner | `release` o `hotfix` según el PR del commit → Environment (R5) |
| `summary.sh` | runner | línea del registro → resumen del job en Markdown |
| `remote/deploy.sh` | VPS, como `deploy` | `apply`, `rollback`, `check` y `status` sobre los bundles ([contrato](../../specs/001-cd-produccion/contracts/remote-scripts.md)) |
| `remote/live-check.sh` + `live-check.js` | VPS | ¿evento o stream en vivo? (R6, FR-028), con el usuario de solo lectura |
| `remote/ensure-ro-user.sh` | VPS | crea o actualiza `vito_deploy_ro` (rol `read` en `vito_events` y `vito_streaming`) |
| `tests/` | CI (`Deploy Scripts`) | `bats` con un `docker` falso y fixtures sin secretos |

## En el VPS

```
/opt/vito/
├── .env                  # secretos (0600), lo escribe cd.yml desde DOTENV_PRODUCTION
├── current -> releases/<sha>
├── releases/<sha>/       # bundle: compose, Caddyfile, srs.conf, remote/*.sh, VERSION, versions.env
├── history               # SHA desplegados con éxito, el último al final
└── deployments.log       # una línea JSON por despliegue o rollback
```

- `versions.env` tiene una `TAG_*` por servicio: el release nuevo copia la del anterior y
  solo cambia los servicios reconstruidos. Compose recrea únicamente lo que cambió (R1).
- Compose siempre corre con `-p vito --project-directory /opt/vito/current`, así las rutas
  de los montajes no cambian entre releases. Un cambio de contenido del Caddyfile o de
  `srs.conf` y `observability/config.alloy` llegan por `CFG_CADDY`, `CFG_SRS` y `CFG_ALLOY`
  (etiqueta `vito.config-hash`) (R4).
- Si la salud falla, `deploy.sh` vuelve solo al bundle anterior. Se conservan los últimos
  3 bundles con éxito y sus imágenes (destinos válidos de rollback).

## Probar en local

```bash
bats infrastructure/deploy/tests                    # VITO_TEST_MONGO=1 suma los tests contra Mongo real
shellcheck infrastructure/**/*.sh
docker compose -f docker-compose.prod.yml \
  --env-file infrastructure/deploy/tests/fixtures/.env.test \
  --env-file infrastructure/deploy/tests/fixtures/versions.env config -q
```

Operación (rollback, forzar en evento, qué hacer con cada código de salida):
[`docs/runbooks/despliegue.md`](../../docs/runbooks/despliegue.md).
