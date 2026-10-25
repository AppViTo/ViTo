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
  > **Actualización 2026-10-19:** el repo pasó a ser público y la protección quedó activa
  > en `main` y `develop` sin cambiar de plan. Las reglas están en `CONTRIBUTING.md` §1.
- `CONTRIBUTING.md` §2 se actualiza con el flujo de release y hotfix.

## Alternativas consideradas

| Alternativa | Por qué no |
|-------------|------------|
| Trunk-based: cada PR de feature directo a `main` y despliegue continuo | Cada merge iría a producción sin una revisión de integración, y con un solo VPS sin staging (ADR-011) el riesgo es mayor. |
| Release con squash de `develop` a `main` | `main` y `develop` divergen en cada release y el siguiente PR arrastra conflictos. |

## Actualizaciones

### 2026-10-24 — Release extraordinario 0.1.0 fuera de la cadencia de los viernes

Se abre el PR de release `develop` → `main` ("release: 2026-10-24", `VERSION` 0.1.0) un
sábado, fuera de la cadencia semanal. Motivo: es el primer despliegue a producción con el
pipeline de la feature 001 (T042) y lo necesita el evento de prueba. Requisitos cumplidos:
triage de seguridad aprobado (T051), T-I06 cerrada y CI verde en `develop`. Aplica las mismas
reglas que un release normal: aprobación de un CODEOWNER distinto del autor, merge commit y
aprobación del despliegue en el Environment `production`. La cadencia de los viernes sigue
vigente para los siguientes releases.

### 2026-10-24 — Excepción: merge del hotfix #192 sin la aprobación del segundo lead

**Qué pasó.** El primer despliegue del release 0.1.0 (#191) se detuvo en Trivy por los
binarios Go de la imagen `mongo-backup`. El hotfix #192 lo corrige (y agrega `/healthz`, T037)
y tenía el CI verde, pero la protección de `main` exige la aprobación de un CODEOWNER distinto
del autor, con "Include administrators" activo. Alex Villa, el único otro lead, no estaba
disponible, y el despliegue llevaba más de 4 horas de retraso frente a lo planeado para el
evento de prueba.

**Decisión.** Con el visto bueno previo y el acuerdo entre los dos leads, Bernardo
(`@bamg359`) desactivó por unos minutos la opción "Include administrators" de la protección de
`main`, mergeó #192 con bypass de administrador (merge commit `c865d68`, 2026-10-25 03:36 UTC)
y la volvió a activar de inmediato. Se comprobó con la API que `enforce_admins` quedó en
`true` y que se mantiene 1 aprobación obligatoria.

**Alcance y mitigación.**
- Solo #192. Ningún otro PR entró sin aprobación, y la regla no cambió.
- El CI de #192 estaba verde (31/31). El despliegue pasa igual por Trivy y por la aprobación
  del Environment `production-hotfix`, con el motivo en el comentario.
- Las excepciones de `.trivyignore` que agrega #192 piden la aprobación de los dos leads. Alex
  revisa #192 después del merge. Si no está de acuerdo, se revierten con un hotfix.

**Por qué es una excepción.** El Principio VIII y este ADR piden dos aprobaciones para
producción: la del PR, de un lead distinto del autor, y la del Environment. Aquí faltó la
primera. No sienta precedente. Si se repite, hay que resolver la falta de un segundo revisor
(D-13 lo resolvió para el rollback, no para los PRs a `main`) en lugar de volver a hacer bypass.

**Segundo PR con la misma excepción (#194).** Con `fail-fast: false`, el despliegue siguiente
mostró que Trivy rechazaba las 13 imágenes. Se usó el mismo procedimiento, con el mismo acuerdo
entre los leads, para mergear #194: Trivy en modo informe hasta el 2026-11-07, con la excepción
A9 de `backend/SECURITY_CHECKLIST.md` y la tarea T-B31. Alex revisa #194 junto con #192.
