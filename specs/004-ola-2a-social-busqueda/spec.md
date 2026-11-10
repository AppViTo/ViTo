# Feature Specification: Ola 2a del contrato — grafo social y búsqueda de usuarios

**Feature Branch**: `contract/ola-2a-spec`

**Created**: 2026-11-09

**Status**: Draft

**Input**: User description: "Ola 2a del contrato (T-C07, D-08): grafo social y búsqueda de usuarios. Alcance decidido por Bernardo (lead) el 2026-11-09: la Ola 2 se divide; esta spec (2a) cubre solo la parte social del inventario (follow, unfollow, perfil social con isFollowing que absorbe is-following, seguidores y seguidos paginados) más searchUsers del RFC #277 con dueño auth-service en GET /api/v1/users/search (se retira el stub de social-service). El check-in/QR de la Ola 2 queda para una spec 2b. Resuelve el arreglo real de Bug-F08 (VITO-83) y el hito 2 del MVP; desbloquea la parte social de T-F02 y de T-B13. Hallazgo: user_profiles de social-service está vacía porque nadie la llena; la spec debe decidir de dónde salen los datos de identidad sin violar el Principio II y, si se puede, sin depender de T-C05. Decisión abierta del RFC #277: si los perfiles privados aparecen en la búsqueda. Tareas separadas por dueño: T-C y T-F para el agente de Alex Villa; T-B y T-I para la sesión de Bernardo."

**Fuente**: `docs/roadmap/endpoint-inventory.md` (sección Social, Ola 2), RFC
[#277](https://github.com/AppViTo/ViTo/issues/277) (`searchUsers`), `docs/roadmap/tasks.md`
(T-C07, Bug-F08, T-F02, T-B13), ADR-002 (array plano), ADR-004 (identidad en `auth-service`),
ADR-016 (un dueño por capacidad).

**Decisiones de los leads (2026-11-09, Bernardo; Alex las ratifica al aprobar D-08 de la Ola 2a)**:

- **La Ola 2 se divide.** Esta spec (2a) cubre el grafo social y la búsqueda de usuarios. El
  check-in/QR (`checkin-service`, 9 operaciones y `/ws/checkin`) va en una spec 2b posterior.
- **Dueño de la búsqueda (RFC #277, decisión 1):** `auth-service`, en una ruta de usuarios
  propia (no bajo `/social`). El stub de búsqueda de `social-service` se retira.
- **`is-following` se fusiona en el perfil social** (inventario: `FUSIONAR→profile.isFollowing`).

**Alcance del contrato** (nombres de operación orientativos; el contrato final los fija):

| # | Operación | Quién la usa | Dueño | Origen |
|---|-----------|--------------|-------|--------|
| 1 | Buscar usuarios (`searchUsers`) | Usuario con sesión | `auth-service` | RFC #277 |
| 2 | Seguir a un usuario (`followUser`) | Usuario con sesión | `social-service` | Inventario |
| 3 | Dejar de seguir (`unfollowUser`) | Usuario con sesión | `social-service` | Inventario |
| 4 | Ver perfil social (`getSocialProfile`), con "lo sigo" y "me sigue" | Usuario con sesión | `social-service` | Inventario (absorbe `is-following`) |
| 5 | Ver seguidores de un usuario (`listFollowers`), paginado | Usuario con sesión | `social-service` | Inventario |
| 6 | Ver a quién sigue un usuario (`listFollowing`), paginado | Usuario con sesión | `social-service` | Inventario |
| 7 | Datos públicos de varios usuarios por lotes (`getUsersByIds`) | `social-service` (y la app, si la necesita) | `auth-service` | Esta spec (FR-012) |

**Fuera de alcance:** check-in/QR (Ola 2b); solicitudes de seguimiento para perfiles privados;
bloquear o silenciar usuarios; sugerencias de "personas que quizá conozcas"; editar el perfil
(nombre, bio, avatar); notificaciones de "te empezó a seguir"; ordenar la búsqueda por "a quién
sigues".

## Clarifications

### Session 2026-11-09

- Q: ¿Qué alcance tiene esta spec? → A: solo la parte social de la Ola 2 y `searchUsers`; el check-in/QR va en una spec 2b (Bernardo).
- Q: ¿Qué servicio es dueño de la búsqueda de usuarios? → A: `auth-service`, en una ruta de usuarios propia (RFC #277, decisión 1; Bernardo).
- Q: ¿De dónde obtiene `social-service` el nombre, el usuario y el avatar? → A: guarda solo el grafo y consulta a `auth-service` por lotes en cada respuesta, con una operación declarada en el contrato; sin copia ni Kafka, así que no depende de T-C05 (Bernardo).
- Q: ¿Los perfiles privados aparecen en la búsqueda? → A: no; a esas personas se las invita por enlace (RFC #277, decisión 2; Bernardo).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El organizador encuentra a quién invitar (Priority: P1)

Como organizador que acaba de crear un evento, quiero escribir parte del nombre o del usuario de
alguien y ver a las personas registradas que coinciden, para elegirlas e invitarlas desde la app.

**Why this priority**: es el arreglo real de Bug-F08. En la prueba de v0.2.0 los dos leads no
pudieron encontrarse para invitarse: el backend de invitaciones ya está en producción, pero sin
búsqueda la invitación desde la app es inutilizable. No depende de nada más de esta ola.

**Independent Test**: con dos cuentas registradas, la cuenta A escribe las dos primeras letras
del nombre visible de B y ve a B en los resultados; la elige y B recibe la invitación al evento.

**Acceptance Scenarios**:

1. **Given** existe "José Pérez" (usuario `jperez`), **When** alguien busca "jose", **Then** José
   aparece en los resultados, aunque el nombre lleve tilde y la búsqueda no.
2. **Given** existe el usuario `caro.mz`, **When** alguien busca "@caro", **Then** `caro.mz`
   aparece (la @ inicial se ignora).
3. **Given** la persona que busca se llama "Carolina", **When** busca "caro", **Then** ella no
   aparece en sus propios resultados.
4. **Given** alguien busca por un correo completo o parte de él, **When** el texto solo coincide
   con el correo de una cuenta, **Then** esa cuenta no aparece, y ningún resultado muestra correo
   ni teléfono.
5. **Given** alguien escribe una sola letra o más de 50 caracteres, **When** busca, **Then** recibe
   un error de validación y no una lista.
6. **Given** alguien lanza muchas búsquedas seguidas, **When** supera el límite por usuario,
   **Then** recibe un aviso de "demasiadas peticiones" con el tiempo de espera.
7. **Given** la búsqueda no encuentra a nadie, **When** responde, **Then** la lista viene vacía y
   la app lo dice con un mensaje claro.

---

### User Story 2 - Seguir y dejar de seguir a alguien (Priority: P1)

Como usuario, quiero seguir a otra persona desde su perfil y dejar de seguirla cuando quiera, para
armar mi red dentro de la app.

**Why this priority**: es el hito 2 del MVP (Follow/Unfollow). Sin él, "Mi Gente" sigue vacía.

**Independent Test**: A sigue a B; el perfil de B muestra un seguidor más y, visto por A, indica
"lo sigues". A deja de seguir a B y los contadores vuelven a su valor.

**Acceptance Scenarios**:

1. **Given** A no sigue a B, **When** A sigue a B, **Then** B tiene un seguidor más, A sigue a una
   persona más y el perfil de B visto por A indica que A lo sigue.
2. **Given** A ya sigue a B, **When** A vuelve a pedir seguir a B (doble toque, reintento de red),
   **Then** la operación termina bien y los contadores no cambian.
3. **Given** A sigue a B, **When** A deja de seguir a B, **Then** los contadores de los dos bajan en
   uno y el perfil de B visto por A indica que ya no lo sigue.
4. **Given** A no sigue a B, **When** A pide dejar de seguir a B, **Then** la operación termina bien
   y nada cambia.
5. **Given** cualquier usuario, **When** intenta seguirse a sí mismo, **Then** recibe un error de
   validación.
6. **Given** el usuario objetivo no existe, **When** alguien intenta seguirlo, **Then** recibe
   "no encontrado".

---

### User Story 3 - Ver el perfil social de una persona (Priority: P2)

Como usuario, quiero abrir el perfil de otra persona y ver su nombre, usuario, avatar, cuántos
seguidores tiene, a cuántos sigue, si yo la sigo y si ella me sigue a mí.

**Why this priority**: es la pantalla desde la que se sigue (US2) y reemplaza los datos falsos de
la app (T-F02). Puede probarse sin la búsqueda, entrando por el identificador del usuario.

**Independent Test**: A abre el perfil de B y ve el nombre y el usuario reales de B, sus contadores
y los dos indicadores de relación; A abre su propio perfil y no ve indicadores de relación.

**Acceptance Scenarios**:

1. **Given** B se registró sin ningún seguidor, **When** A abre su perfil, **Then** ve el nombre y
   el usuario de B, 0 seguidores, 0 seguidos, "no lo sigues" y "no te sigue".
2. **Given** B sigue a A, **When** A abre el perfil de B, **Then** ve "te sigue".
3. **Given** A abre su propio perfil, **When** se muestra, **Then** los indicadores de relación
   vienen en falso y la app no ofrece "seguir".
4. **Given** el usuario no existe, **When** alguien abre su perfil, **Then** recibe "no encontrado".
5. **Given** B se registró antes de esta ola, **When** alguien abre su perfil, **Then** ve sus datos
   reales: los usuarios existentes no quedan "sin perfil".

---

### User Story 4 - Ver seguidores y seguidos (Priority: P3)

Como usuario, quiero ver la lista de seguidores de una persona y la de quienes sigue, por páginas,
con su nombre y avatar, para descubrir gente y entrar a sus perfiles.

**Why this priority**: completa el grafo social del inventario, pero las US1–US3 ya entregan valor
sin ella.

**Independent Test**: con 25 seguidores de B, la primera página de 20 trae 20 personas, la segunda
trae 5, y el total de las dos páginas coincide con el contador del perfil de B.

**Acceptance Scenarios**:

1. **Given** B tiene 25 seguidores, **When** alguien pide la página 0 con tamaño 20 y luego la
   página 1, **Then** recibe 20 y 5 personas, sin repetidas ni faltantes.
2. **Given** las listas se recorren por páginas, **When** se piden, **Then** el orden es estable:
   primero los seguimientos más recientes.
3. **Given** B no sigue a nadie, **When** alguien pide a quién sigue B, **Then** recibe una lista
   vacía, no un error.
4. **Given** el usuario no existe, **When** alguien pide sus seguidores, **Then** recibe "no
   encontrado".

---

### Edge Cases

- **Usuarios registrados antes de esta ola:** deben poder buscarse, seguirse y verse con sus datos
  reales desde el primer día, sin que tengan que volver a entrar ni editar nada.
- **Cambio de nombre visible o avatar:** el perfil social, las listas y la búsqueda muestran el dato
  nuevo en la siguiente consulta, porque se leen de `auth-service` en cada respuesta (FR-012).
- **Seguimientos concurrentes:** si A y C siguen a B al mismo tiempo, o A sigue y deja de seguir en
  ráfaga, los contadores terminan coincidiendo con las relaciones reales y nunca bajan de 0.
- **Cuenta inexistente o eliminada** en una lista de seguidores: no rompe la página; se omite o se
  muestra sin datos, nunca como error 500.
- **Perfil privado** (`privateProfile`): ver FR-004. Esta ola no añade solicitudes de seguimiento.
- **Texto de búsqueda con caracteres especiales** (comillas, `.*`, emojis): se trata como texto
  literal y nunca como patrón.
- **Paginación fuera de rango** (página más allá del final): lista vacía, no error.

## Requirements *(mandatory)*

### Functional Requirements

**Búsqueda de usuarios (`auth-service`)**

- **FR-001**: El sistema DEBE permitir a un usuario con sesión buscar usuarios registrados por
  prefijo de su nombre de usuario o de cualquier palabra de su nombre visible, sin distinguir
  mayúsculas ni tildes. Una @ inicial en el texto se ignora.
- **FR-002**: El texto de búsqueda DEBE tener entre 2 y 50 caracteres; fuera de ese rango, error de
  validación. Las páginas tienen como máximo 20 resultados.
- **FR-003**: Los resultados DEBEN ordenarse así: primero el nombre de usuario exacto, luego los
  prefijos de nombre de usuario, luego los prefijos de nombre visible; a igual coincidencia, por
  nombre de usuario ascendente. El orden es estable entre páginas.
- **FR-004**: La búsqueda NUNCA DEBE buscar por correo ni teléfono ni devolverlos, y NUNCA incluye a
  quien busca. Los perfiles marcados como privados NO aparecen en la búsqueda; a esas personas se
  las invita por enlace (RFC #277, decisión 2).
- **FR-005**: Cada resultado DEBE traer solo datos públicos: identificador, nombre de usuario, nombre
  visible (puede faltar) y avatar (puede faltar).
- **FR-006**: El sistema DEBE limitar la búsqueda por usuario (referencia del RFC: 1 por segundo con
  ráfaga de 10) y, al superarlo, responder "demasiadas peticiones" con el tiempo de espera.
- **FR-007**: Los usuarios registrados antes de esta ola DEBEN poder encontrarse desde el despliegue,
  sin acción de su parte.
- **FR-008**: La ruta de búsqueda vieja de `social-service` (que siempre devolvía vacío y no está en el
  contrato) DEBE retirarse, y la app DEBE usar la nueva.

**Grafo social (`social-service`)**

- **FR-009**: Un usuario con sesión DEBE poder seguir y dejar de seguir a otro usuario. Las dos
  operaciones son idempotentes: repetirlas no es un error y no cambia contadores. Seguirse a sí mismo
  es un error de validación; seguir o dejar de seguir a un usuario inexistente es "no encontrado".
- **FR-010**: El perfil social de un usuario DEBE mostrar identificador, nombre de usuario, nombre
  visible, avatar, número de seguidores, número de seguidos, "lo sigo" y "me sigue". Para el propio
  perfil, los dos indicadores son falsos. Los indicadores se nombran con su prefijo (`isFollowing`,
  `isFollowedBy`) en la respuesta, sin el defecto de T-B13.
- **FR-011**: Los contadores de seguidores y seguidos DEBEN coincidir con las relaciones existentes,
  también ante peticiones concurrentes, y nunca ser negativos.
- **FR-012**: El perfil social, la lista de seguidores y la de seguidos DEBEN mostrar el nombre de
  usuario, nombre visible y avatar reales de cada persona, incluidos los usuarios registrados antes de
  esta ola, sin que `social-service` lea la base de `auth-service` (Principio II). Para eso,
  `social-service` guarda solo el grafo y, en cada respuesta, pide a `auth-service` los datos de
  identidad de los usuarios de esa página con **una sola consulta por lotes** (a lo sumo el tamaño
  de página). Esa operación por lotes se declara en el contrato (Principio I) y solo devuelve datos
  públicos (FR-005). La colección `user_profiles` de `social-service` deja de ser fuente de identidad.
- **FR-012a**: Si `auth-service` no responde a tiempo, el perfil social y las listas DEBEN fallar con
  un error claro de servicio no disponible (envelope ADR-003), nunca con un 500 genérico ni con
  datos inventados. Un identificador que `auth-service` ya no conoce se omite de la página.
- **FR-013**: Las listas de seguidores y seguidos DEBEN paginarse en el origen (no traer todo y
  recortar), devolver un array plano (ADR-002) y ordenarse por fecha de seguimiento descendente, con
  desempate estable. Tamaño de página por defecto 20, máximo 50.
- **FR-014**: La consulta "¿A sigue a B?" como operación aparte se retira; su respuesta queda dentro
  del perfil social.
- **FR-015**: Todas las operaciones de esta ola requieren sesión, responden los errores con el envelope
  único (ADR-003) y figuran en `openapi.yaml` con `operationId`, ejemplos y fixtures 2xx y de error
  antes de implementarse (Principio I).
- **FR-016**: Seguir y dejar de seguir DEBEN dejar un registro del cambio que otros servicios puedan
  consumir en el futuro (notificaciones, recomendaciones). Si eso exige un evento Kafka nuevo, se
  publica con el sobre versionado y el outbox del Principio V, o se deja fuera de la ola y se registra
  como deuda junto a T-B36; nunca "dispara y olvida".

**App (agente de Alex)**

- **FR-017**: La pantalla "¿A quién invitamos?" DEBE usar la búsqueda nueva y retirar el aviso
  provisional de Bug-F08.
- **FR-018**: "Mi Gente" y el perfil de otra persona DEBEN mostrar datos reales del grafo social y de la
  búsqueda, sin `MockApiService` (parte social de T-F02), con estados vacíos claros.

### Key Entities

- **Usuario (identidad)**: dueño `auth-service`. Identificador, nombre de usuario (único), nombre visible
  opcional, avatar opcional, marca de perfil privado. Correo y teléfono nunca salen de `auth-service`.
- **Resultado de búsqueda**: vista pública de un usuario: identificador, nombre de usuario, nombre
  visible y avatar.
- **Relación de seguimiento**: dueño `social-service`. Quién sigue, a quién sigue y cuándo empezó. Una
  sola relación por par; no existe la relación de alguien consigo mismo.
- **Perfil social**: vista de un usuario desde `social-service`: datos de identidad (según FR-012),
  contadores de seguidores y seguidos, y la relación con quien consulta ("lo sigo", "me sigue").

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En una prueba con los dos leads, cada uno encuentra al otro escribiendo como máximo 3
  letras de su nombre o usuario y le envía una invitación a un evento en menos de 30 segundos (cierra
  Bug-F08 / VITO-83).
- **SC-002**: El 100 % de los usuarios registrados antes del despliegue aparecen en la búsqueda (salvo
  lo que decida FR-004) y tienen perfil social con sus datos reales desde el primer día.
- **SC-003**: El 95 % de las búsquedas devuelve resultados en menos de 1 segundo, medido en un entorno
  representativo.
- **SC-004**: Tras 1 000 operaciones de seguir y dejar de seguir concurrentes sobre los mismos usuarios,
  los contadores coinciden al 100 % con las relaciones reales.
- **SC-005**: Recorrer por páginas una lista de 500 seguidores devuelve cada seguidor exactamente una
  vez.
- **SC-006**: Ninguna respuesta de esta ola contiene un correo o un teléfono (verificado con los
  fixtures y con una prueba automática).
- **SC-007**: La app deja de usar datos falsos en "Mi Gente" y en el perfil de otra persona.

## Assumptions

- La identidad (nombre de usuario, nombre visible, avatar, perfil privado) vive en `auth-service`
  (ADR-004); esta ola no añade edición de perfil.
- Cualquier usuario con sesión puede ver el perfil social y las listas de seguidores y seguidos de
  cualquier otro; la privacidad del perfil solo afecta a la búsqueda (FR-004). Las solicitudes de
  seguimiento quedan fuera.
- Seguir y dejar de seguir son idempotentes (ver FR-009), a diferencia del código actual, que responde
  409 al repetir; gana el contrato (ADR-010).
- Los contadores pueden guardarse o calcularse; lo que se exige es que coincidan con las relaciones
  (FR-011).
- El límite de la búsqueda va a CONVENTIONS §9 como excepción, igual que la vista previa del enlace.
- La ola se publica en `contract/ola-2a` tras la aprobación D-08 de la Ola 2a por los dos leads; el
  check-in/QR (Ola 2b) necesitará su propia aprobación.
- Las tareas se reparten por dueño: contrato (T-C) y app (T-F) para el agente de Alex Villa; backend
  (T-B) e infraestructura (T-I: ruta del gateway, límite de tasa, migraciones e índices en producción)
  para la sesión de Bernardo.
- **Dependencias:** RFC #277 (se absorbe en esta spec y se cierra con su merge); T-B13 (parte social);
  T-F02 (parte social). Por la decisión de FR-012, la ola no depende de T-C05.
- **Descartado para FR-012:** que la app complete los datos (dos llamadas por pantalla) y la copia de
  perfiles por eventos (outbox, sobre versionado y `asyncapi.yaml`, varias veces más trabajo). La copia
  puede volver si la consulta por lotes no cumple el rendimiento o la disponibilidad que hacen falta.
