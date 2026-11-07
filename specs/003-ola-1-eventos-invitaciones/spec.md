# Feature Specification: Ola 1 del contrato — eventos, invitaciones y RSVP

**Feature Branch**: `contract/ola-1-spec`

**Created**: 2026-11-01

**Status**: Draft

**Input**: User description: "Ola 1 del contrato (T-C07, D-08): eventos, invitaciones y RSVP. Alcance aprobado por Bernardo (lead) el 2026-11-01: las 8 operaciones de la Ola 1 de docs/roadmap/endpoint-inventory.md (updateEvent PUT /events/{eventId}; cancelar con DELETE /events/{eventId} que pasa el evento a CANCELLED sin borrarlo; RSVP POST /events/{eventId}/rsvp con ACCEPTED/DECLINED/MAYBE/WAITLIST y cupo atómico; GET /events/{eventId}/attendees paginado; POST y GET /events/{eventId}/invitations solo organizador; GET /auth/me) + los enlaces de invitación y scope=invited del RFC #204 (createInviteLink, listInviteLinks, revokeInviteLink, getInviteLinkPreview público, acceptInviteLink). D-01: invitations/respond se FUSIONA en /rsvp (una sola forma de responder; si hay invitación, su estado se actualiza). searchUsers queda en la Ola 2. Resuelve Bug-F07 (editar evento) y el enlace real de Bug-F09; hitos MVP 4 y 5. Las tareas deben separarse por dueño: contrato y frontend para el agente de Alex, backend/infra para nosotros."

**Fuente**: `docs/roadmap/endpoint-inventory.md` (Ola 1), RFC [#204](https://github.com/AppViTo/ViTo/issues/204)
(sin `searchUsers`), `docs/roadmap/tasks.md` (T-C07, Bug-F07, Bug-F09), ADR-014 (listado de
eventos), ADR-016 (dueños de dominio; fila "Invitación vs RSVP" pendiente de esta ola).

**Decisiones de los leads (2026-11-01, Bernardo; Alex las ratifica al aprobar D-08 de la Ola 1)**:

- **Alcance:** las 8 operaciones de la Ola 1 del inventario más los enlaces de invitación y
  `scope=invited` del RFC #204. La búsqueda de usuarios (`searchUsers`, Bug-F08) se queda en la Ola 2.
- **Invitación vs RSVP (cierra la fila pendiente de ADR-016):** hay una sola forma de responder,
  el RSVP. Si la persona tiene una invitación, el RSVP actualiza también su estado. La ruta
  `invitations/respond` del backend actual se retira.
- **Cancelar no es borrar:** el organizador cancela el evento; el evento y sus invitaciones y
  respuestas se conservan.
- **Asistentes (Bernardo y Alex):** la lista es solo del organizador; los invitados ven un
  contador de confirmados para generar expectativa del evento.

**Alcance del contrato** (nombres de operación orientativos; el contrato final los fija):

| # | Operación | Quién la usa | Origen |
|---|-----------|--------------|--------|
| 1 | Editar evento (`updateEvent`) | Organizador | Inventario |
| 2 | Cancelar evento (`cancelEvent`) | Organizador | Inventario |
| 3 | Responder RSVP (`respondRsvp`) | Usuario con sesión | Inventario (absorbe `invitations/respond`) |
| 4 | Ver asistentes (`listEventAttendees`) | Solo el organizador; los demás ven el número de confirmados en el detalle del evento | Inventario |
| 5 | Invitar a un usuario registrado (`sendInvitation`) | Organizador | Inventario / #204 |
| 6 | Ver invitaciones del evento (`listEventInvitations`) | Organizador | Inventario / #204 |
| 7 | Mi perfil de sesión (`getCurrentUser`) | Usuario con sesión | Inventario |
| 8 | Crear enlace de invitación (`createInviteLink`) | Organizador | #204 |
| 9 | Ver enlaces del evento (`listInviteLinks`) | Organizador | #204 |
| 10 | Revocar enlace (`revokeInviteLink`) | Organizador | #204 |
| 11 | Vista previa del enlace (`getInviteLinkPreview`) | Cualquiera, sin sesión | #204 |
| 12 | Unirse por enlace (`acceptInviteLink`) | Usuario con sesión | #204 |
| 13 | Listar eventos a los que me invitaron (`listEvents` con `scope=invited`) | Usuario con sesión | #204 |

## Clarifications

### Session 2026-11-01

- Q: ¿Qué alcance tiene la Ola 1? → A: las 8 operaciones del inventario más los enlaces de invitación y `scope=invited` del RFC #204; `searchUsers` queda en la Ola 2 (Bernardo).
- Q: ¿Cómo responde un invitado, por `invitations/respond` o por `/rsvp`? → A: solo por `/rsvp`; si hay invitación, su estado se actualiza y `invitations/respond` se retira (D-01, Bernardo).
- Q: ¿Qué hace `DELETE /events/{eventId}`? → A: cancela el evento (`CANCELLED`) y conserva invitaciones y respuestas (Bernardo).
- Q: ¿Qué ve un asistente confirmado en la lista de asistentes? → A: nada; la lista es solo del organizador y los invitados ven un contador de confirmados (Bernardo y Alex).
- Q: ¿Qué dato de ubicación muestra la vista previa pública del enlace a alguien sin sesión? → A: el nombre del lugar (`details.location`); nunca la dirección exacta ni las coordenadas, igual que para un invitado que no ha aceptado.
- Q: Si alguien respondió "tal vez" a un evento privado y su invitación vence, ¿puede seguir cambiando su respuesta? → A: sí; tener cualquier respuesta registrada da acceso al evento mientras esté vigente, aunque la invitación venza. Solo pierde el acceso quien nunca respondió.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El organizador comparte un enlace y sus invitados se unen (Priority: P1)

Como organizador, quiero crear un enlace de invitación y compartirlo por WhatsApp o copiándolo,
para que mis invitados, tengan o no cuenta en ViTo, lleguen a mi evento y confirmen su asistencia.

**Why this priority**: es el hito 4 del MVP ("Invitaciones: app + links externos") y el canal
principal de crecimiento (KPI conversión invitado → registro > 70 %). Hoy la app ofrece "Copiar
enlace" y "Enviar por WhatsApp" pero el enlace nunca se genera (Bug-F09).

**Independent Test**: crear un evento, generar su enlace, abrirlo en un navegador sin sesión,
registrarse, aceptar y comprobar que el evento aparece en "Próximos eventos" del invitado y que
el organizador ve el uso del enlace.

**Acceptance Scenarios**:

1. **Given** un evento publicado del que soy organizador, **When** creo un enlace sin indicar
   vigencia, **Then** obtengo una URL compartible que vence a los 7 días y empieza con 0 usos.
2. **Given** un enlace vigente, **When** alguien sin sesión lo abre, **Then** ve el título, la
   fecha, el nombre del lugar (sin dirección exacta ni coordenadas), la portada, la categoría y el
   nombre del organizador, con la opción de iniciar sesión o registrarse para unirse.
3. **Given** un enlace vigente y una persona con sesión, **When** se une, **Then** queda invitada
   con su asistencia confirmada, el enlace suma un uso y el evento aparece en su lista de eventos
   a los que fue invitada.
4. **Given** una persona que ya se unió, **When** vuelve a usar el mismo enlace, **Then** recibe
   su invitación existente y el enlace no suma otro uso.
5. **Given** un enlace vencido, revocado o inexistente, **When** alguien lo abre o intenta
   unirse, **Then** ve el mismo mensaje "El enlace no existe o ya no está disponible", sin
   distinguir el caso.
6. **Given** un enlace con límite de usos alcanzado, o un evento cancelado o terminado, **When**
   alguien con sesión intenta unirse, **Then** se rechaza con un mensaje claro y no se crea nada.
7. **Given** varios enlaces de mi evento, **When** los consulto, **Then** veo cada uno con su
   vigencia, usos, límite y si está revocado, los vigentes primero.
8. **Given** un enlace que compartí por error, **When** lo revoco, **Then** deja de funcionar de
   inmediato y quienes ya se unieron conservan su invitación.

---

### User Story 2 - El organizador edita su evento (Priority: P1)

Como organizador, quiero cambiar la fecha, la foto, la descripción o el lugar de mi evento,
para corregir errores o adaptarme a cambios sin tener que crear otro evento (Bug-F07).

**Why this priority**: es un bug P0 reportado en producción (VITO-82): un organizador no puede
corregir su propio evento.

**Independent Test**: editar la fecha y la descripción de un evento existente y comprobar que
el detalle muestra los datos nuevos para el organizador y para un invitado.

**Acceptance Scenarios**:

1. **Given** un evento del que soy organizador, **When** cambio uno o varios de sus datos,
   **Then** el evento queda guardado con los cambios y el resto de los datos intactos.
2. **Given** un evento de otra persona, **When** intento editarlo, **Then** se rechaza por falta
   de permiso.
3. **Given** un evento con 30 asistentes confirmados, **When** intento bajar el cupo a 20,
   **Then** se rechaza explicando que el cupo no puede quedar por debajo de los confirmados.
4. **Given** una fecha de fin anterior a la de inicio, o datos que no cumplen las reglas de
   creación, **When** guardo, **Then** se rechaza con el detalle del campo inválido.
5. **Given** un evento cancelado o terminado, **When** intento editarlo, **Then** se rechaza.

---

### User Story 3 - Los invitados responden si van (Priority: P1)

Como invitado (o como interesado en un evento público), quiero responder que voy, que no voy o
que tal vez, para que el organizador sepa con quién contar; si el cupo está lleno, quiero quedar
en lista de espera.

**Why this priority**: es el hito 5 del MVP ("RSVP: opciones múltiples") y la condición para
que un asistente pueda tener su QR de entrada (Ola 2).

**Independent Test**: con un evento de cupo 2, que tres personas confirmen y comprobar que dos
quedan confirmadas, la tercera en lista de espera, y que al retirarse una confirmada la primera
de la lista pasa a confirmada.

**Acceptance Scenarios**:

1. **Given** un evento publicado al que tengo acceso, **When** respondo "voy", **Then** quedo
   confirmado si hay cupo.
2. **Given** un evento con el cupo completo, **When** respondo "voy", **Then** quedo en lista de
   espera y se me informa.
3. **Given** dos personas que responden "voy" al mismo tiempo por el último lugar, **When** se
   procesan, **Then** solo una queda confirmada y la otra en lista de espera; el cupo nunca se
   supera.
4. **Given** una persona confirmada, **When** cambia su respuesta a "no voy" o "tal vez",
   **Then** libera su lugar y la primera persona de la lista de espera pasa a confirmada.
5. **Given** que tengo una invitación pendiente, **When** respondo, **Then** mi invitación
   refleja la respuesta (aceptada o rechazada) y no existe otra forma de responder la invitación.
6. **Given** un evento privado al que no fui invitado, **When** intento responder, **Then** se
   rechaza como si el evento no existiera para mí.
7. **Given** un evento cancelado o terminado, **When** intento responder, **Then** se rechaza.
8. **Given** que ya respondí, **When** envío la misma respuesta otra vez, **Then** el resultado
   es el mismo y no se duplica nada.

---

### User Story 4 - El organizador invita a usuarios de ViTo y ve a quién invitó (Priority: P2)

Como organizador, quiero invitar desde la app a personas que ya usan ViTo y ver el estado de
todas las invitaciones, para saber quién falta por responder.

**Why this priority**: complementa al enlace (US1). Sin búsqueda de usuarios (Ola 2) solo se
puede invitar a quien ya se conoce por su identificador, así que vale menos que el enlace hoy.

**Independent Test**: invitar a un usuario existente, comprobar que la invitación aparece
pendiente en la lista del organizador y que el evento aparece en la lista de eventos a los que
fue invitado el usuario.

**Acceptance Scenarios**:

1. **Given** un evento del que soy organizador y un usuario registrado, **When** lo invito con
   un mensaje opcional, **Then** queda una invitación pendiente que vence a los 7 días.
2. **Given** un usuario ya invitado a ese evento, **When** lo invito de nuevo, **Then** se
   rechaza indicando que ya está invitado.
3. **Given** un evento de otra persona, **When** intento invitar o ver sus invitaciones,
   **Then** se rechaza por falta de permiso (hoy cualquier usuario puede ver los invitados de
   cualquier evento; esta ola lo cierra).
4. **Given** mis invitaciones, **When** las consulto, **Then** veo cada una con su estado, si
   llegó por enlace o por la app, y las fechas de envío y respuesta, por páginas.
5. **Given** una invitación pendiente que superó su vencimiento, **When** se consulta, **Then**
   aparece como vencida y el invitado ya no puede responder por ella.

---

### User Story 5 - El organizador cancela su evento (Priority: P2)

Como organizador, quiero cancelar un evento que no se va a realizar, para que deje de mostrarse
como próximo y nadie se presente.

**Why this priority**: necesaria para operar eventos reales, pero menos frecuente que editar.

**Independent Test**: cancelar un evento con invitados y comprobar que desaparece de los
listados de próximos eventos, que su detalle lo muestra como cancelado y que no se pueden
crear respuestas nuevas.

**Acceptance Scenarios**:

1. **Given** un evento publicado del que soy organizador, **When** lo cancelo, **Then** queda
   como cancelado, conserva sus invitaciones y respuestas, y sale de los listados de próximos e
   invitados.
2. **Given** un evento cancelado, **When** un invitado consulta su detalle, **Then** ve que está
   cancelado.
3. **Given** un evento de otra persona, **When** intento cancelarlo, **Then** se rechaza.
4. **Given** un evento ya cancelado, **When** lo cancelo de nuevo, **Then** el resultado es el
   mismo, sin error.

---

### User Story 6 - Ver quién asiste y cuántos van (Priority: P2)

Como organizador, quiero ver la lista de asistentes con su respuesta, para coordinar el evento.
Como invitado, quiero ver cuántas personas ya confirmaron, para saber que el evento va a estar
bueno, sin ver quiénes son.

**Why this priority**: la lista es útil para el organizador el día del evento y el contador
genera expectativa entre los invitados; ninguno bloquea el flujo de invitar y responder.

**Decisión de los leads (2026-11-01, Bernardo y Alex):** la lista de asistentes es solo del
organizador (privacidad, Principio IX). Los demás ven únicamente el número de confirmados.

**Independent Test**: con respuestas de varios tipos, comprobar que el organizador ve la lista
completa, que un invitado confirmado y uno pendiente ven el mismo número de confirmados y que
ninguno de los dos puede ver la lista.

**Acceptance Scenarios**:

1. **Given** un evento del que soy organizador, **When** consulto los asistentes, **Then** veo a
   todos los que respondieron, con su respuesta, por páginas.
2. **Given** que puedo ver un evento (estoy invitado o el evento es público), **When** abro su
   detalle, **Then** veo cuántas personas confirmaron, sin nombres ni ninguna otra información
   de quiénes son.
3. **Given** que no soy el organizador, aunque esté confirmado, **When** consulto la lista de
   asistentes, **Then** se rechaza por falta de permiso.
4. **Given** que alguien confirma o deja su lugar, **When** otro invitado vuelve a abrir el
   detalle, **Then** el número de confirmados está actualizado.
5. **Given** la lista del organizador, **When** se muestra, **Then** nunca incluye correo ni
   teléfono.

---

### User Story 7 - La app recupera mi sesión al abrirse (Priority: P3)

Como usuario con sesión iniciada, quiero que la app me reconozca al abrirse y muestre mis datos,
sin volver a escribir mi contraseña.

**Why this priority**: mejora la experiencia (auto-login); hoy la app ya funciona con los datos
que recibe al iniciar sesión.

**Independent Test**: abrir la app con una sesión guardada y comprobar que muestra el nombre del
usuario sin pedir credenciales; con una sesión revocada, que lleva al inicio de sesión.

**Acceptance Scenarios**:

1. **Given** una sesión válida, **When** la app pide mis datos, **Then** recibe mi perfil con la
   misma forma que en el inicio de sesión.
2. **Given** una sesión vencida o revocada, **When** la app pide mis datos, **Then** se rechaza
   como no autenticada.

---

### Edge Cases

- El organizador intenta unirse a su propio evento por enlace: se responde con éxito y no se
  crea nada ni se suma un uso.
- Alguien prueba tokens al azar o en masa: el mismo "no disponible" para todo token inválido y
  límite de intentos por IP y por usuario.
- Se edita la fecha de un evento con invitados ya confirmados: los datos cambian y las
  respuestas se conservan. Avisar a los invitados queda fuera de esta ola (es una feature con
  su propio contrato de notificaciones).
- Se edita un evento para ocultar la dirección hasta la aceptación: los no confirmados dejan de
  verla de inmediato.
- Se cancela un evento con lista de espera: nadie más cambia de estado.
- Un usuario invitado por la app también se une por enlace: se reutiliza su invitación.
- Un enlace sin límite de usos en un evento con cupo: unirse confirma si hay lugar y deja en
  lista de espera si no; el enlace nunca supera el cupo del evento.
- Un evento pasa su fecha de fin: deja de aceptar respuestas y uniones, y sale de los listados
  de próximos e invitados.
- Una invitación vencida sin respuesta: el invitado aún puede unirse por un enlace vigente o
  responder si el evento es público. Si ya había respondido (incluido "tal vez"), conserva el
  acceso y puede cambiar su respuesta.

## Requirements *(mandatory)*

### Functional Requirements

**Enlaces de invitación**

- **FR-001**: El organizador DEBE poder crear enlaces de invitación de su evento, con vigencia de
  1 a 30 días (7 por defecto) y un límite opcional de usos (1 a 1000; sin límite por defecto).
- **FR-002**: Cada enlace DEBE tener un identificador secreto imposible de adivinar (al menos 128
  bits aleatorios). La dirección pública del enlace se arma con un dominio configurado por
  entorno, nunca escrito en el repositorio.
- **FR-003**: Cualquiera, sin sesión, DEBE poder ver la vista previa de un enlace vigente: título,
  fechas, nombre del lugar (el mismo dato que ve un invitado que no ha aceptado), portada,
  categoría, nombre del organizador y vencimiento del enlace. La vista previa NUNCA incluye la
  dirección exacta ni coordenadas, sin importar la configuración del evento.
- **FR-004**: Un usuario con sesión DEBE poder unirse por un enlace vigente. Al unirse queda
  invitado y responde "voy" con las mismas reglas de cupo del RSVP (FR-014 a FR-016).
- **FR-005**: Unirse DEBE ser idempotente: si la persona ya tiene invitación al evento, se
  devuelve la existente sin sumar un uso; si es el organizador, se responde con éxito sin crear
  nada.
- **FR-006**: Un enlace vencido, revocado o inexistente DEBE responder igual en la vista previa
  y al unirse ("El enlace no existe o ya no está disponible"), sin revelar cuál es el caso.
- **FR-007**: Unirse DEBE rechazarse si el enlace llegó a su límite de usos o si el evento está
  cancelado o terminado.
- **FR-008**: El organizador DEBE poder ver los enlaces de su evento, por páginas y con los
  vigentes primero, y revocar cualquiera. Revocar no afecta a quienes ya se unieron.
- **FR-009**: La vista previa y la unión por enlace DEBEN tener límite de intentos por IP y por
  usuario, con indicación de cuándo reintentar.

**Invitaciones desde la app**

- **FR-010**: El organizador DEBE poder invitar a un usuario registrado con un mensaje opcional
  (hasta 280 caracteres). La invitación vence a los 7 días si no se responde.
- **FR-011**: Invitar a alguien ya invitado al mismo evento DEBE rechazarse como conflicto.
- **FR-012**: Solo el organizador DEBE poder ver las invitaciones de su evento, por páginas, con
  su estado, origen (app o enlace) y fechas.
- **FR-013**: Cada usuario DEBE poder listar los eventos a los que fue invitado (invitación
  pendiente vigente o aceptada, o con una respuesta suya registrada), publicados y no terminados, ordenados por fecha de inicio, con las
  mismas reglas de visibilidad de dirección que el resto de listados.

**RSVP**

- **FR-014**: Un usuario con acceso al evento DEBE poder responder "voy", "no voy" o "tal vez", y
  cambiar su respuesta mientras el evento no haya terminado ni esté cancelado. Tiene acceso
  quien fue invitado (por la app o por enlace) con la invitación vigente, quien ya tiene una
  respuesta registrada al evento (aunque su invitación haya vencido) o, si el evento es
  público, cualquier usuario con sesión.
- **FR-015**: Con el cupo completo, responder "voy" DEBE dejar a la persona en lista de espera e
  informarlo en la respuesta. El cupo NUNCA se supera, aunque lleguen respuestas simultáneas.
- **FR-016**: Cuando un confirmado deja su lugar, la primera persona de la lista de espera (por
  orden de llegada) DEBE pasar a confirmada.
- **FR-017**: Si la persona tiene una invitación, su respuesta DEBE actualizar el estado de la
  invitación (aceptada con "voy" o lista de espera, rechazada con "no voy"; "tal vez" la deja
  pendiente). El RSVP es la única forma de responder una invitación; la ruta separada para
  responder invitaciones se retira.
- **FR-018**: Para un evento privado sin invitación, responder DEBE rechazarse como si el evento
  no existiera para esa persona.
- **FR-019**: Enviar la misma respuesta varias veces DEBE dar el mismo resultado sin duplicados.

**Gestión del evento**

- **FR-020**: El organizador DEBE poder editar los datos de su evento (título, descripción,
  fechas, lugar, portada, categoría, cupo, visibilidad y ocultar dirección), enviando solo los
  datos que cambian. Se aplican las mismas validaciones que al crear.
- **FR-021**: Bajar el cupo por debajo del número de confirmados DEBE rechazarse.
- **FR-022**: El organizador DEBE poder cancelar su evento. Cancelar conserva el evento, sus
  invitaciones y sus respuestas; el evento sale de los listados de próximos e invitados y no
  admite respuestas, uniones ni ediciones nuevas. Cancelar dos veces no es un error.
- **FR-023**: Solo el organizador DEBE poder editar o cancelar su evento.
- **FR-024**: Solo el organizador DEBE poder ver la lista de asistentes de su evento, con su
  respuesta, por páginas. La lista nunca incluye correo ni teléfono. Cualquier otra persona,
  aunque esté confirmada, recibe un rechazo por falta de permiso.
- **FR-024a**: Quien puede ver el detalle de un evento (invitado o, si es público, cualquier
  usuario con sesión) DEBE ver el número de personas confirmadas, sin datos que permitan saber
  quiénes son. El número se actualiza al confirmar o liberar un lugar. No incluye la lista de
  espera ni las respuestas "no voy" o "tal vez".

**Sesión**

- **FR-025**: Un usuario con sesión válida DEBE poder obtener su propio perfil, con la misma
  forma que el perfil que recibe al iniciar sesión. Una sesión vencida o revocada se rechaza.

**Transversales**

- **FR-026**: Toda operación de esta ola DEBE estar en el contrato antes de implementarse, con
  ejemplos, errores con el formato único del proyecto y casos de ejemplo ejecutables (Principio I).
- **FR-027**: Todas las respuestas de error DEBEN usar el envelope único de errores y mensajes en
  español; los listados usan la paginación estándar del proyecto (máximo 100 por página).
- **FR-028**: Las fechas DEBEN viajar en UTC con zona explícita (lección de Bug-B01).
- **FR-029**: Crear, editar, cancelar, invitar, responder y unirse por enlace DEBEN publicar su
  evento de dominio para que otros servicios (check-in, mosaico, notificaciones futuras) reaccionen.
- **FR-030**: Las rutas del backend actual que esta ola reemplaza o retira (responder invitación
  por ruta separada) DEBEN dejar de estar expuestas al público.

### Key Entities

- **Evento**: lo que se organiza. Datos ya existentes (título, descripción, fechas, lugar,
  portada, categoría, cupo, visibilidad, ocultar dirección) y estado (borrador, publicado,
  cancelado, terminado). Esta ola agrega la edición y la cancelación.
- **Invitación**: vínculo entre un evento y una persona invitada. Estado (pendiente, aceptada,
  rechazada, vencida), quién invitó, mensaje, origen (app o un enlace concreto), fechas de envío,
  vencimiento y respuesta. Una por persona y evento.
- **Enlace de invitación**: puerta de entrada compartible a un evento. Identificador secreto,
  dirección pública, vencimiento, límite de usos opcional, usos, revocado o no. Pertenece a un
  evento; puede originar muchas invitaciones.
- **Respuesta (RSVP)**: decisión de una persona sobre un evento. Estado (pendiente, confirmada,
  rechazada, tal vez, lista de espera) y orden de llegada para la lista de espera. Una por
  persona y evento. Base del futuro ticket QR (Ola 2).
- **Vista previa del enlace**: versión pública y reducida del evento para quien no tiene sesión.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un organizador crea y comparte el enlace de su evento en menos de 30 segundos
  desde el detalle del evento.
- **SC-002**: Una persona sin cuenta que abre el enlace puede registrarse y quedar confirmada en
  menos de 2 minutos, y el evento aparece en su lista de invitados sin pasos adicionales.
- **SC-003**: Con 50 respuestas simultáneas a un evento de cupo 20, quedan exactamente 20
  confirmados y 30 en lista de espera, en el 100 % de las pruebas.
- **SC-004**: El 100 % de las operaciones de la ola está en el contrato, con al menos un caso de
  ejemplo de éxito y uno de error cada una, y sus pruebas de contrato pasan en la app y en el
  backend.
- **SC-005**: Ningún usuario que no sea el organizador puede ver invitaciones, enlaces ni la
  lista completa de asistentes de un evento ajeno (0 casos en las pruebas de permisos).
- **SC-005a**: Todo invitado ve en el detalle del evento el número de confirmados, y coincide con
  la lista del organizador en el 100 % de las pruebas.
- **SC-006**: Un enlace vencido, revocado o inventado produce exactamente la misma respuesta en
  el 100 % de los casos.
- **SC-007**: Bug-F07 y la parte de enlace de Bug-F09 se cierran: un organizador edita fecha,
  foto, descripción y lugar de su evento, y los botones "Copiar enlace" y "Enviar por WhatsApp"
  entregan un enlace que funciona.
- **SC-008**: Las respuestas de RSVP y de unión por enlace se perciben inmediatas (por debajo de
  1 segundo en el 95 % de los casos en producción).

## Assumptions

- **Ticket QR fuera de esta ola.** Según ADR-016, el ticket QR y el check-in son de
  `checkin-service` y entran en la Ola 2. En la Ola 1, confirmar asistencia no devuelve un QR;
  la Ola 2 lo emite a partir de las respuestas confirmadas.
- **Búsqueda de usuarios fuera.** Sin `searchUsers` (Ola 2), invitar desde la app requiere el
  identificador del usuario; el canal principal de esta ola es el enlace. Bug-F08 sigue con su
  arreglo inmediato (aviso en pantalla).
- **Notificaciones fuera.** Avisar a los invitados de cambios o cancelaciones es una feature con
  su propio contrato (pedido en VITO-82, no procede como bug).
- **Estado "lista de espera".** En el contrato se llama `WAITLISTED`, como ya fija CONVENTIONS
  §6; la persona no lo elige, lo asigna el sistema al responder "voy" sin cupo.
- **Borradores.** Los eventos nacen publicados (Bug-B02); esta ola no agrega un flujo de
  borrador y publicación.
- **El backend existente es referencia, no fuente de verdad** (ADR-010): `event-service` ya
  tiene edición, cancelación, RSVP, asistentes e invitaciones, y `auth-service` tiene el perfil
  de sesión; donde difieran del contrato, gana el contrato. Se corrige además el hallazgo del
  #204: hoy cualquiera puede listar las invitaciones de cualquier evento.
- **Fechas existentes.** Los eventos ya guardados no requieren migración para editar o cancelar.
- **Reparto de tareas por dueño** (lo concreta `/speckit-tasks`): contrato (`docs/api/**`) y app
  Flutter para el agente de Alex; `event-service`, `auth-service`, gateway e infraestructura
  (variable del dominio del enlace, rutas públicas, límites de intentos) para la sesión de
  backend de Bernardo.
- **Aprobación:** esta spec se mergea como borrador; el contrato de la ola se mergea solo con la
  aprobación de D-08 de la Ola 1 por los dos leads y de 1 CODEOWNER.
