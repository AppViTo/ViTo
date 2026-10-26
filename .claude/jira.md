# Configuración de Jira para el agente `jira-scribe`

Lo lee el agente `jira-scribe` (definido en `~/.claude/agents/jira-scribe.md` de cada
miembro, o en el plugin del equipo) antes de cada pedido. **Sin secretos:** el repo es
público. La autenticación la da el conector de Atlassian de cada persona.

## Sitio y proyecto

| Campo | Valor |
|-------|-------|
| Sitio | `bernardo-mira.atlassian.net` |
| `cloudId` | `d8ca89e9-71b6-4c98-a3cb-d4c92e28b079` |
| Proyecto | `VITO` |
| Fuente de verdad | `docs/roadmap/tasks.md` en la rama `develop`. Jira es el espejo, en un solo sentido: repo → Jira |

## Identificar un issue

- El resumen empieza con el ID del repo: `T-B26 · Moderación por el organizador`.
- Antes de crear, buscar con JQL: `project = VITO AND summary ~ "T-B26"`.
- Las decisiones `D-xx` van en la descripción de las historias afectadas, salvo que se pida
  crearlas como issue propio.
- Las tareas de una feature de Spec Kit (`specs/<n>/tasks.md`, p. ej. `T048`) van como
  subtareas de su historia, con la etiqueta `spec-<n>`.

## Épicas por pista

| Pista (prefijo) | Épica |
|-----------------|-------|
| Contrato (`T-C`) | VITO-6 |
| Frontend (`T-F`) | VITO-7 |
| Backend (`T-B`) | VITO-8 |
| Infra (`T-I`) | VITO-9 |

## Estados

| Estado en `tasks.md` | Estado en Jira | Etiqueta |
|----------------------|----------------|----------|
| 🟢 READY | Tareas por hacer | `ready` |
| 🟡 RFC | Tareas por hacer | `rfc` |
| 🔴 BLOQUEADA | Tareas por hacer, marcada como bloqueada (Flagged, `customfield_10021` = "Impediment") | `bloqueada` |
| 🔵 EN CURSO | En curso | — |
| ✅ HECHA | Listo | `hecha` |
| ⚪ DIFERIDA / REEMPLAZADA | Tareas por hacer, con un comentario que explica el motivo | `diferida` |

Si el nombre de una transición no coincide, usar la más cercana y avisar con `DUDA`.

## Etiquetas

- Pista: `pista-c`, `pista-f`, `pista-b`, `pista-i`.
- Prioridad: `p0`, `p1`, `p2`. VITO no tiene campo de prioridad: la prioridad va solo como
  etiqueta.
- Manuales de leads: `manual`.

## Vínculos

- Un bloqueo `T-x` → `T-y` va como vínculo "Blocks" (`T-x` blocks `T-y`).
- Al cerrar, comentar con el PR: `Hecha en #189`.

## No tocar

- VITO-1 a VITO-5: historias previas, a pedido de Bernardo.
- VITO-3 (CD con Vercel) contradice ADR-011; es una decisión de los leads.
