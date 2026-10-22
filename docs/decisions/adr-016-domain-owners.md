# ADR-016: Un solo servicio dueño por capacidad duplicada

- **Estado:** Aceptada
- **Fecha:** 2026-10-22
- **Decisor:** Ambos leads (se formaliza con la aprobación del PR)
- **Resuelve:** D-01 de `docs/roadmap/decisions-pending.md` y O-30 de la constitución
- **Relacionado:** ADR-010 (el backend es referencia), ADR-011 (Kafka de un broker),
  ADR-015 (identidad en el WS de chat), `docs/roadmap/endpoint-inventory.md`

## Contexto

El inventario del 2026-10-16 encontró la misma capacidad implementada en dos servicios.
Ninguna de las rutas duplicadas está en `openapi.yaml`, así que por el Principio I ninguna
está publicada; el frontend sí llama a las dos variantes en varios casos.

| Capacidad | Implementación A | Implementación B |
|-----------|------------------|------------------|
| QR + check-in | `event-service`: `POST /events/{id}/check-in`, `GET /events/{id}/qr-code` | `checkin-service`: `/checkin/ticket`, `/checkin/validate`, stats, recent |
| Subida de medios | `media-service`: `POST /media/upload` (en contrato) | `mosaic-service`: `POST /mosaic/photo` |
| Ítem de check-in en el mosaico | `mosaic-service`: `POST /mosaic/checkin` (ruta pública) | — |
| Chat | `chat-service` (salas, mensajes) | `streaming-service`: `POST/GET /streaming/{id}/chat` |
| Info de stream | `GET /streaming/{id}` | `GET /streaming/{id}/info` |
| Invitación vs RSVP | `POST /events/{id}/invitations/respond` | `POST /events/{id}/rsvp` |

Hechos relevantes del código actual:

- `checkin-service` publica en el tópico `checkin-events`, pero **ningún servicio tiene un
  `@KafkaListener`**: hoy nadie consume esos eventos.
- `checkin-service` no conoce la capacidad ni el estado del evento; esos datos viven en
  `event-service`.
- Dos dueños para la misma regla (por ejemplo, el cupo del check-in) significan dos
  implementaciones que se desincronizan, y el frontend no sabe a cuál llamar.

## Decisión

1. **QR y check-in → `checkin-service`.** Emite el ticket QR (`/checkin/ticket`), lo valida
   (`/checkin/validate`) y expone stats, recientes y revocación. Se retiran
   `POST /events/{id}/check-in` y `GET /events/{id}/qr-code` de `event-service`.
   - `checkin-service` necesita la capacidad y el estado del evento. El mecanismo (API de
     `event-service` o réplica local alimentada por `event-events`) se fija en la spec de la
     **Ola 2**, con una restricción: validar un QR no puede depender de una llamada síncrona
     a otro servicio por escaneo, porque la meta es < 200 ms p99.
2. **Subida de medios → `media-service`.** Sube, guarda y procesa el archivo
   (`POST /media/upload`). `mosaic-service` solo crea el ítem del mosaico con el `mediaId`
   ya subido (`POST /mosaic/post`). Se retira `POST /mosaic/photo`.
3. **Ítem de check-in en el mosaico → interno.** `mosaic-service` lo crea al consumir
   `checkin-events`. `POST /mosaic/checkin` no se enruta en el gateway ni entra a
   `openapi.yaml`.
4. **Chat → `chat-service`.** El chat de un stream es una sala de `chat-service` ligada al
   `streamId`. Se retiran `POST/GET /streaming/{id}/chat` de `streaming-service`.
5. **Info de stream → una sola ruta.** `GET /streaming/{id}` devuelve todo lo que hoy
   devuelve `/info`. Se retira `GET /streaming/{id}/info`.
6. **Invitación vs RSVP → queda en `event-service`; la forma se fija en la Ola 1.** Criterio:
   se mantienen las dos rutas solo si la semántica difiere (aceptar una invitación a un
   evento privado frente a confirmar asistencia a uno público). Si no difiere,
   `invitations/respond` se fusiona en `/rsvp`. La spec de la Ola 1 lo decide y lo deja
   escrito; no hace falta otro ADR salvo que cambie un dueño.
7. **Regla general:** cada capacidad tiene un solo servicio dueño. Si otro servicio
   necesita el dato o el efecto, lo pide por API interna o lo consume por Kafka; no
   reimplementa la regla ni expone una ruta pública paralela.

## Consecuencias

- **Contrato (T-C07):** las olas publican solo las rutas del dueño. En
  `endpoint-inventory.md` las filas `FUSIONAR` e `INTERNO` de esta tabla dejan de estar
  pendientes de D-01. T-C07 queda bloqueada solo por D-08 (aprobación por ola).
- **Ola 2:** `checkin-service` define cómo obtiene capacidad y estado del evento y es dueño
  del cupo del check-in. `event-service` borra sus dos rutas de check-in/QR al implementar
  la ola.
- **Ola 3:** el flujo de subida del frontend pasa a dos pasos: `POST /media/upload` y luego
  `POST /mosaic/post` con el `mediaId`. `mosaic-service` agrega el primer consumidor de
  `checkin-events` y retira `/mosaic/photo` y `/mosaic/checkin` del gateway. T-F05 queda
  bloqueada solo por la Ola 3.
- **Ola 4:** el stream del MVP lleva reacciones, pero **no chat**. El chat del stream llega
  con `chat-service` en la Ola 6 (post-MVP). Si los leads quieren chat en el stream para el
  MVP, hay que adelantar a la Ola 4 la parte de salas de `chat-service`.
- **Frontend:** deja de llamar a las rutas retiradas cuando entre la ola correspondiente
  (QR/check-in en la 2, subida en la 3, info y chat de stream en la 4). Mientras tanto no
  se tocan, porque ninguna de las dos variantes está en el contrato.
- **Backend:** el código de las rutas retiradas se borra en la ola que las reemplaza, no
  antes, para no romper pantallas que todavía las usan contra el backend local.

## Alternativas consideradas

| Alternativa | Por qué no |
|-------------|------------|
| QR y check-in en `event-service` (se elimina `checkin-service`) | `event-service` concentraría CRUD, RSVP, invitaciones y la validación de baja latencia. `checkin-service` ya tiene ticket, stats, recientes y su WS. |
| Fusionar `event-service` + `checkin-service` y `media-service` + `mosaic-service` (D-02.d) | ADR-011 mantuvo los servicios separados en la Fase Tracción; la fusión es una decisión de despliegue aparte y no resuelve qué ruta publicar. |
| Mantener `POST /mosaic/photo` como atajo de un solo paso | Duplica almacenamiento, validación y moderación de medios en `mosaic-service`. |
| Chat propio en `streaming-service` | Dos modelos de mensajes, dos reglas de moderación y dos WS de chat. |
| Decidir ya invitación vs RSVP | Depende del modelo de eventos privados, que se especifica en la Ola 1; decidirlo aquí sería adivinar. |
