# ADR-010: El código backend existente es referencia, no fuente de verdad

- **Estado:** Aceptada
- **Fecha:** 2026-09-25
- **Decisor:** Equipo frontend (lead), decisión de gobierno acordada con el equipo backend

## Contexto

El monorepo contiene ya un backend considerable (~93 endpoints Spring + 15 FastAPI) que **nadie en este ciclo escribió siguiendo el contrato**. Hay:

- **Deriva documental:** `docs/api/openapi.yaml` cubre 14 paths de ~108; `docs/api/README.md` documenta ~17 endpoints que no existen con esos paths (`DELETE /social/unfollow/{id}` vs real `DELETE /social/follow/{id}`; `GET /events/user` vs real `GET /events/my-events`; `POST /mosaic/upload` vs real `POST /mosaic/post`; y ~14 más).
- **Código muerto:** `backend/events-service/` (un solo archivo de 0 bytes, sin `pom.xml`), `ChatWebSocketHandler`/`MosaicWebSocketHandler`/`StreamingWebSocketHandler` sin registrar, `ShareServiceImpl` sin ruta expuesta, 5 clases `*Application.java` de 0 bytes.
- **Rotos en runtime:** los 5 endpoints de `recommendation-service` y 2 de `moderation-service` lanzan `AttributeError`/`NameError` (`request.app.state` sobre un body Pydantic); motores de IA son placeholders.
- **CI incompleto:** solo 1 de 10 servicios Java corre en CI; `docker-build` falla porque `backend/auth-service` no tiene `Dockerfile`.
- **`README.md` raíz corrupto** (cola UTF-16 incrustada), `mvnw` documentado pero inexistente.

## Decisión

1. **La fuente de verdad del qué debe existir es `docs/api/openapi.yaml` + `asyncapi.yaml` + `docs/handoff/*`.** El código backend existente no define el contrato.
2. **El equipo backend decide libremente** si reutiliza, reescribe o descarta cada parte de ese código. No se le obliga a conservarlo ni a reescribirlo.
3. **Nada se implementa** contra el código existente sin haberlo contrastado antes con el contrato.
4. Los artefactos derivados del código viejo que contradicen el contrato se **corrigen o se marcan obsoletos** (`docs/api/README.md`).
5. Esta decisión no juzga la calidad del código: se usa como **pista de esfuerzo estimado y de comportamiento probable**, no como especificación.

## Consecuencias

- Los handoffs describen el *qué* (endpoints, payloads, reglas), no el *cómo*. El backend elige su implementación.
- Ante cualquier contradicción "el código dice X / el contrato dice Y", **gana el contrato**; se abre un RFC si el backend necesita excepción.
- Se pueden deprecar/mover a `backend/archive/` las carpetas sin build (`events-service`, `integration-tests` sin `pom.xml`) — decisión del equipo backend, no bloqueante.
- Los 10 desajustes ya detectados se registran como ADR-001…009 (decisión de forma) y esta (decisión de gobierno).

## Actualizaciones

### 2026-10-12 — se elimina `backend/events-service/`

Aplicando la consecuencia anterior, se borra `backend/events-service/`: solo contenía un
`EventsApplication.java` sin `pom.xml` y no formaba parte de ningún build. El servicio de eventos
es **`backend/event-service/`** (paquete `com.vito.event`), que es el que enruta el gateway
(`/api/v1/events/**` → `event-service`). `AGENTS.md` y `docs/handoff/create-event.md` se corrigen
para usar ese nombre.

## Alternativas consideradas

| Alternativa | Por qué se descarta |
|-------------|---------------------|
| **Reconciliar y reparar el backend existente como base obligatoria** | Obliga a discutir cada línea heredada; acopla ambos equipos a código que ninguno escribió en este ciclo. |
| **Descartar el backend por completo** | Desperdicia trabajo útil (los controllers ya coinciden en gran parte con el contrato) y alarga el MVP. |
| **Que el backend defina el contrato y el frontend se adapte** | Contradice la decisión de que manda el frontend; además el backend no tiene un contrato interno coherente que ofrecer. |
