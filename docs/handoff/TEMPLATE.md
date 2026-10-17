# HANDOFF — `<feature>`

> **Para quién es:** el agente/desarrollador del equipo backend que implementa esta feature.
> **Regla:** este documento describe el *qué*. Tú decides el *cómo*. Si necesitas cambiar algo de aquí, abre un RFC (issue con template `contract-change`) — **no diverjas del contrato en tu código**.

| | |
|---|---|
| **Equipo responsable** | Backend: `<servicio>` · Frontend: `<pantalla(s)>` |
| **Contrato** | [`docs/api/openapi.yaml`](../api/openapi.yaml) (tag `<tag>`) · [`docs/api/asyncapi.yaml`](../api/asyncapi.yaml) si aplica |
| **Fixtures** | `docs/api/fixtures/<feature>/` |
| **Convenciones generales** | [`docs/api/CONVENTIONS.md`](../api/CONVENTIONS.md) — leer antes de empezar |
| **Estado** | 🔴 No iniciado · 🟡 En progreso · 🟢 Completo |
| **Ola / tareas** | Ola `<N>` de [`docs/roadmap/README.md`](../roadmap/README.md) · tareas `T-xx` de [`tasks.md`](../roadmap/tasks.md) |
| **Contexto de agente** | [`AGENT-BRIEF.md`](./AGENT-BRIEF.md) — leer antes de empezar |

---

## 1. Contexto

<!-- 2-4 líneas: qué resuelve esta feature para el usuario final y por qué importa. -->

## 2. Endpoints

> Formato obligatorio por operación. Los fixtures de `docs/api/fixtures/<feature>/` son el ejemplo canónico.
> El `openapi.yaml` es la referencia ejecutable: si este documento y el YAML discrepan, **gana el YAML**.

### `METHOD /api/v1/path`

- **Auth:** `bearerAuth` / `X-User-Id` / público
- **Rate limit:** N req/s
- **KPI objetivo:** (si aplica, p.ej. "validación < 200ms")
- **Request:**
  ```json
  { }
  ```
- **Response 200/201:**
  ```json
  { }
  ```
- **Errores:**

  | Código | `error` | Cuándo |
  |--------|---------|--------|
  | 400 | `VALIDATION_ERROR` | falta `title` |
  | 404 | `NOT_FOUND` | evento inexistente |

## 3. Esquemas

<!-- Referencia a los schemas del openapi.yaml. Si un schema es nuevo, añadirlo al YAML en el mismo PR que este handoff. -->

## 4. Reglas de negocio

<!-- Lo que NO se deduce del payload. Ejemplos:
 - Check-in: el QR es un JWT firmado con RSA, expira al cierre del evento, valida firma + expiración + cupo de forma atómica.
 - Mosaico: al cerrar el evento el grid queda congelado y no acepta más items.
 Cada regla debe ser verificable con un test. -->

1.

## 5. WebSockets (si aplica)

- **Ruta:** `/ws/<recurso>/{id}?token=<jwt>` — ver ADR-006
- **Servidor → cliente:**

  | `type` | `data` | Cuándo |
  |--------|--------|--------|
  | `NEW_ITEM` | `{ }` | nuevo item en el mosaico |

- **Cliente → servidor:**

  | `type` | Campos | Efecto |
  |--------|--------|--------|
  | `SEND_MESSAGE` | `roomId`, `content` | persiste y retransmite |

- **Frame de ejemplo:**
  ```json
  { "type": "NEW_ITEM", "data": { } }
  ```

## 6. Dependencias

| Qué | Quién lo da | Estado |
|-----|-------------|--------|
| Ruta `/ws/**` en el gateway | equipo backend (gateway) | 🔴 |
| Headers `X-User-*` | gateway (ADR-007) | 🟢 |
| Kafka topic `x-events` | — | |

## 7. Criterios de aceptación

<!-- Checklist verificable. Cada punto debe poderse probar con un comando o una acción manual. -->

- [ ] El endpoint está implementado y responde **exactamente** igual que el fixture de `docs/api/fixtures/<feature>/`
- [ ] `npx @stoplight/spectral-cli lint docs/api/openapi.yaml` pasa
- [ ] `schemathesis run --checks all <url-del-servicio>` pasa
- [ ] Errores devuelven el envelope de ADR-003 con `message` en es-ES
- [ ] Listas devuelven array plano (ADR-002) y aplican `page`/`size` reales (ADR-008)
- [ ] Enums exactos de `CONVENTIONS.md` §6
- [ ] `mvn clean test` verde con tests unitarios de los casos de negocio de §4
- [ ] (Si aplica) los frames WS coinciden con `asyncapi.yaml`
- [ ] Ningún endpoint nuevo fuera de `openapi.yaml`

## 8. Fuera de alcance

<!-- Lo que explícitamente NO entra en esta feature, para evitar trabajo perdido. -->

## 9. Preguntas abiertas

<!-- Dudas técnicas para el equipo frontend. Se resuelven antes del kickoff del lunes. -->

| # | Pregunta | Respuesta |
|---|----------|-----------|

---
*Última actualización: `AAAA-MM-DD` · Autor: @<quien>*
