# ADR-012: Ramas, release semanal y promoción a producción

- **Estado:** Aceptada
- **Fecha:** 2026-10-17
- **Decisor:** Ambos leads (acordado el 2026-10-17; se formaliza con la aprobación del PR)
- **Resuelve:** D-07 de `docs/roadmap/decisions-pending.md` y la observación O-24
- **Relacionado:** ADR-011 (el despliegue a producción sale de `main`)

## Contexto

`CONTRIBUTING.md` definía `develop` como rama de integración, pero varios PR fueron
directo a `main`, y `deploy-frontend.yml` despliega desde `main`. ADR-011 fija que
producción se despliega desde `main` y que la aprobación manual es la del PR que entra
a `main`. El plan Free de GitHub no permite protección de rama ni revisores obligatorios
en Environments de repos privados.

## Decisión

| Rama / flujo | Regla |
|--------------|-------|
| `develop` | Integración. **Todo PR de feature** (`contract/`, `frontend/`, `backend/`, `docs/`) apunta aquí. Merge con **squash**. |
| `main` | Producción. Solo recibe PR de release o de hotfix. Cada commit que entra a `main` se despliega (ADR-011). |
| Release semanal | Los **viernes**, tras la review de integración, un lead abre el PR `release: AAAA-MM-DD` de `develop` → `main`. Requiere CI verde y la aprobación ("Approve" en GitHub) de 1 CODEOWNER distinto del autor: **esa aprobación es la aprobación manual de producción**. Se mergea con **merge commit**, no con squash, para que `develop` y `main` no diverjan. |
| Tag | Al mergear el release se crea el tag `vMAJOR.MINOR.PATCH` sobre `main` (fase pre-1.0: `v0.MINOR.PATCH`). La imagen de cada servicio lleva el SHA (Principio VIII). |
| Hotfix | Rama `hotfix/<nombre>` desde `main` → PR a `main` (misma aprobación) → al mergear, PR de `main` a `develop` para traer el arreglo de vuelta. |
| Release vacío | Si `develop` no tiene cambios desde el último release, no se abre PR. |
| Contenido del PR de release | Lista de PR incluidos (títulos Conventional Commits), tareas `T-xx` cerradas, cambios de contrato o de configuración que exigen acción (variables de entorno, migraciones) y plan de rollback (SHA anterior). |

## Consecuencias

- Acción manual de los leads: en GitHub, **cambiar la rama por defecto a `develop`**
  (Settings → General → Default branch), para que los PR nuevos apunten ahí por defecto.
  `deploy-frontend.yml` y `cd.yml` siguen escuchando `main`.
- La cadencia de producción es semanal. Un arreglo urgente usa el flujo de hotfix y no
  espera al viernes.
- Como no hay protección de rama, la regla "sin push directo a `main`" se cumple por
  acuerdo del equipo. Al pasar a GitHub Team se activa la protección en `main` y `develop`.
- `CONTRIBUTING.md` §2 se actualiza con el flujo de release y hotfix.

## Alternativas consideradas

| Alternativa | Por qué no |
|-------------|------------|
| Trunk-based: cada PR de feature directo a `main` y despliegue continuo | Cada merge iría a producción sin una revisión de integración, y con un solo VPS sin staging (ADR-011) el riesgo es mayor. |
| Release con squash de `develop` a `main` | `main` y `develop` divergen en cada release y el siguiente PR arrastra conflictos. |

## Actualizaciones

*(ninguna todavía)*
