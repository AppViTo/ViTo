# Hoja de ruta — Alineación contrato ↔ implementación

> **Estado:** vigente desde 2026-10-16 · **Dueño:** leads (backend @bamg359 + frontend)
> **Autoridad:** esta hoja de ruta es guía operativa. Está subordinada a la constitución
> (`.specify/memory/constitution.md`), los ADR y `docs/api/CONVENTIONS.md`. Si algo aquí
> los contradice, ganan ellos y se corrige este documento.

## Por qué existe

El código (100 operaciones: 87 Java + 13 FastAPI) va muy por delante del contrato
(15 operaciones en `openapi.yaml`). El circuito **contrato → fixture → mock → test → E2E**
no está cerrado. Esta hoja de ruta lo cierra en olas, siguiendo el flujo del MVP, y
define qué puede implementarse **ya** y qué espera una decisión.

## Documentos de la ruta

| Documento | Para qué |
|-----------|----------|
| [`tasks.md`](./tasks.md) | Backlog único con ID, estado, dependencias y criterio de cierre. **Ningún agente trabaja fuera de él.** |
| [`endpoint-inventory.md`](./endpoint-inventory.md) | Las 100 operaciones del código con la decisión propuesta (publicar / fusionar / interno / diferir) y la ola en que entran al contrato. |
| [`decisions-pending.md`](./decisions-pending.md) | Decisiones abiertas (D-xx) que bloquean tareas. Cada una se cierra con un ADR. |
| [`../handoff/AGENT-BRIEF.md`](../handoff/AGENT-BRIEF.md) | Contexto completo para cualquier agente implementador. Se lee antes de tomar una tarea. |

## Etapas

```
Etapa 0  Gobierno         constitución v1.1.0 · hoja de ruta · brief        ✅ este PR
Etapa 1  Decisiones       D-01/02/03/04/06/07/09 ✅ (ADR-011..016); quedan    ⏳ leads
                          D-05, D-08, D-10..D-12
Etapa 2  Circuito cerrado fixtures + Prism + Spectral/AsyncAPI en CI          🟢 se puede empezar ya
         + tests Dart de fixtures, sobre las 15 operaciones existentes
Etapa 3  Endurecimiento   gateway (X-User-*, /ws/**, ADR-003), WS crudos,    🟢 se puede empezar ya
         sin cambio de contrato  errores ADR-003, hideAddressUntilAccepted
Etapa 4  Contrato por ola contract/<ola> → handoff → back + front mismo sprint 🔴 tras D-08 (por ola)
Etapa 5  Infra / CD       Fase Tracción (ADR-011): VPS + Compose + R2        🟡 T-B14, T-I07, T-I09,
                          T-I10, T-I12 listas; el resto tras T-I06 (leads)
```

### Olas de contrato (Etapa 4)

Cada ola es **una feature Spec Kit** (`/speckit-specify` → … → `/speckit-implement`) y
**una rama `contract/<ola>`** que se mergea antes de implementar.

| Ola | Alcance | Hito MVP (AGENTS.md) | Bloqueada por |
|-----|---------|----------------------|---------------|
| 0 | Cerrar las 15 ops existentes: fixtures, mock, `GET /events` (O-26) | — | D-03 (solo `GET /events`) |
| 1 | Auth completo (`/auth/me`) + Events CRUD + RSVP + Invitaciones | 1, 3, 4, 5 | D-08 |
| 2 | Check-in/QR + Social | 2, 6, 7 | D-08 |
| 3 | Media + Mosaico (REST + WS + Kafka) | 8 | D-08 |
| 4 | Streaming (REST + WS) | 9 | D-08 |
| 5 | Archivo post-evento | 10 | D-08 |
| 6 | Chat, recomendaciones, moderación (post-MVP) | — | D-08 |

## Reglas de ejecución (resumen; detalle en el brief)

1. Solo se toman tareas en estado 🟢 `READY` de `tasks.md`. Las 🟡 `RFC` se pueden
   **redactar** pero no mergear sin aprobación de un CODEOWNER.
2. Una tarea = una rama = un PR. El PR cita el ID (`Closes T-B01`) y actualiza el
   estado en `tasks.md`.
3. Tarea atómica (≤ 1 día, sin contrato nuevo) → directo. Ola de contrato → Spec Kit.
4. Si al implementar aparece algo no previsto que toca contrato → se detiene, se abre
   issue `contract-change` y se registra como nueva tarea/decisión. Nunca se resuelve
   en el código.

## Seguimiento

- Viernes (review de integración): se revisan `tasks.md` y `decisions-pending.md`.
- Al cerrar una observación O-xx de la constitución se marca en su tabla con enmienda PATCH.
