# Feature Specification: Spring Boot con soporte en los 10 servicios Java

**Feature Branch**: `backend/t-b31-spring-boot-soporte`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "T-B31: Migrar los 10 servicios Java de backend a una versión de Spring Boot con soporte que elimine los CVE HIGH/CRITICAL que Trivy reporta por imagen. Absorbe T-B10 (versiones unificadas). Fecha límite: excepción A9 de Trivy vence el 2026-11-07."

**Fuente**: `docs/roadmap/tasks.md`, T-B31 (absorbe T-B10, O-21 y O-35 de la constitución).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - El escáner de seguridad deja de encontrar CVE corregibles en las imágenes Java (Priority: P1)

Como responsable de despliegue, quiero que cada imagen Java de backend pase el escaneo de Trivy sin vulnerabilidades HIGH/CRITICAL que tengan arreglo, para que la excepción A9 no sea necesaria y el despliegue no quede bloqueado el 2026-11-08.

**Why this priority**: sin esto, a partir del 2026-11-08 ningún despliegue a producción pasa el CD. Es el motivo de la tarea y tiene fecha límite.

**Independent Test**: escanear las 10 imágenes Java con Trivy tras la migración y comprobar que no hay HIGH/CRITICAL con arreglo disponible.

**Acceptance Scenarios**:

1. **Given** las 10 imágenes Java construidas desde la rama de la migración, **When** se ejecuta Trivy con la misma política de severidad del CD, **Then** ninguna imagen reporta HIGH/CRITICAL con versión de arreglo disponible.
2. **Given** una vulnerabilidad sin arreglo upstream, **When** se escanea, **Then** queda documentada con su justificación y fecha de revisión en la lista de excepciones, y no bloquea el despliegue.
3. **Given** la migración terminada, **When** se revisan las excepciones A9, **Then** la excepción queda retirada antes del 2026-11-07.

---

### User Story 2 - Los servicios siguen funcionando igual para los clientes (Priority: P1)

Como miembro del equipo frontend, quiero que la migración no cambie ningún contrato REST ni WebSocket, para que la app no necesite cambios.

**Why this priority**: un cambio de versión mayor de framework puede alterar serialización, códigos de error y seguridad sin que se note en compilación.

**Independent Test**: ejecutar los tests de contrato y el smoke E2E contra el entorno de staging levantado con las imágenes migradas.

**Acceptance Scenarios**:

1. **Given** los fixtures de `docs/api/fixtures/`, **When** se ejecutan los tests de contrato de cada servicio, **Then** pasan sin cambiar los fixtures.
2. **Given** un token expirado o ausente, **When** se llama a una ruta protegida, **Then** la respuesta es `401` con el envelope de ADR-003.
3. **Given** el gateway migrado, **When** un cliente abre `/ws/**` con `?token=` válido e inválido, **Then** el comportamiento es el mismo que antes (conexión o cierre `4401`).

---

### User Story 3 - Una sola versión de Spring Boot y de Java en todo el backend (Priority: P2)

Como responsable de backend, quiero que los 10 servicios usen la misma versión de Spring Boot y de Java, para que un parche de seguridad se aplique una vez y no servicio por servicio (absorbe T-B10, O-21 y O-35).

**Why this priority**: hoy conviven 3.2.1 (10 poms) y 3.5.3 (2 poms) sobre Java 17. Unificar reduce el coste de cada CVE futuro, pero no es lo que bloquea el despliegue.

**Independent Test**: comprobar que todos los `pom.xml` (o el parent común) declaran la misma versión de Spring Boot y de Java.

**Acceptance Scenarios**:

1. **Given** el repositorio tras la migración, **When** se busca la versión de Spring Boot en todos los poms, **Then** solo aparece una versión.
2. **Given** el repositorio tras la migración, **When** se revisa la versión de Java de compilación e imagen, **Then** es la misma en los 10 servicios y en CI.

---

### User Story 4 - El resto del backend (Python, gateway y CI) no se rompe (Priority: P2)

Como miembro del equipo de infraestructura, quiero que CI y el despliegue sigan verdes durante y después de la migración.

**Why this priority**: la migración toca 10 servicios y el pipeline; un fallo silencioso en CI retrasa el release.

**Independent Test**: ejecutar el CI completo sobre la rama y comprobar que todos los jobs obligatorios pasan.

**Acceptance Scenarios**:

1. **Given** la rama de la migración, **When** se ejecuta el CI, **Then** los jobs de build, tests y contrato de los servicios Java pasan.
2. **Given** el CD con Trivy en modo bloqueante, **When** se despliega en staging, **Then** el despliegue completa sin excepciones A9 activas.

---

### Edge Cases

- ¿Qué pasa si Spring Cloud Gateway no tiene una versión compatible con la línea elegida? La migración del gateway se bloquea y no se puede desplegar un gateway con versiones mezcladas.
- ¿Qué pasa si algún CRITICAL solo tiene arreglo en Spring Framework 7 y la línea elegida no lo incluye? Ese CVE sigue bloqueando el despliegue y la decisión de versión debe revisarse.
- ¿Qué pasa si Jackson 3 cambia la serialización de algún DTO? Los tests de contrato de los fixtures deben detectarlo antes del merge.
- ¿Qué pasa si una propiedad de configuración del gateway se renombra? El servicio arranca con valores por defecto y el fallo aparece en runtime; el smoke debe comprobarlo.
- ¿Qué pasa si el despliegue de producción ocurre a mitad de migración? Los servicios no migrados y los migrados coexisten; el contrato no cambia, así que la mezcla es aceptable solo durante la ventana de despliegue.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Los 10 servicios Java de backend (gateway, auth, social, event, checkin, mosaic, streaming, archive, chat, media) DEBEN ejecutarse sobre una misma versión de Spring Boot con soporte del proveedor.
- **FR-002**: Las imágenes Java DEBEN compilar y ejecutarse con la misma versión mayor de Java, que la constitución fija en Java 21 (O-21).
- **FR-003**: Trivy, con la política de severidad del CD, DEBE pasar sobre las 10 imágenes Java sin HIGH/CRITICAL con arreglo disponible, salvo excepciones documentadas.
- **FR-004**: Ninguna excepción A9 DEBE seguir activa después del 2026-11-07.
- **FR-005**: Los contratos REST y WebSocket existentes DEBEN seguir igual: los fixtures de `docs/api/fixtures/` pasan sin modificarse.
- **FR-006**: Los errores de los servicios DEBEN seguir el envelope de ADR-003, incluidos los `401` y `403` de la seguridad.
- **FR-007**: El gateway DEBE seguir eliminando los `X-User-*` entrantes, validando `?token=` en `/ws/**` y cerrando con `4401` (Principio III).
- **FR-008**: Cada servicio DEBE conservar su capa de dominio y sus puertos sin dependencias de framework nuevas (Principio II).
- **FR-009**: La migración DEBE documentar, por servicio, los cambios de configuración que requiera (propiedades, Jackson, seguridad).
- **FR-010**: Los tests unitarios e integración de los 10 servicios DEBEN pasar en CI antes del merge (Principio IV).
- **FR-011**: La migración NO DEBE incluir cambios de funcionalidad ni de contrato; cualquier hallazgo se registra como tarea nueva.

### Key Entities *(include if feature involves data)*

No introduce entidades de datos nuevas. Afecta a la configuración de cada servicio (versiones, propiedades de Spring y del gateway) y al registro de excepciones de seguridad.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Las 10 imágenes Java pasan el escaneo de Trivy sin HIGH/CRITICAL con arreglo disponible antes del 2026-11-07.
- **SC-002**: El número de excepciones A9 activas es 0 a partir de la fecha de cierre de la migración.
- **SC-003**: El 100% de los tests de contrato de los fixtures pasa sin cambios en los fixtures.
- **SC-004**: El número de versiones distintas de Spring Boot y de Java en el backend es 1 cada una.
- **SC-005**: Un despliegue a staging con el CD completo termina sin intervención manual y sin excepciones A9.
- **SC-006**: Ningún cliente de la app necesita cambios para seguir funcionando tras la migración.

## Assumptions

- La constitución (Principio X, O-21, O-35) fija Java 21 y una única versión de Spring Boot soportada. Spring Boot 4 sale de "3.x LTS" y requiere enmienda (ADR-018).
- El primer escaneo de Trivy (release 0.1.0) reportó de 39 a 57 CVE HIGH/CRITICAL con arreglo por imagen Java; la cifra se reverifica al empezar.
- La excepción A9 vence el 2026-11-07; desde el 2026-11-08 Trivy bloquea el despliegue.
- Los fixtures de `docs/api/fixtures/` son la referencia de contrato; no se modifican para hacer pasar la migración.
- La migración se hace en una sola rama y un solo PR por servicio o por grupo, según el tamaño que decida el equipo en el plan.
- Spring Cloud Gateway y la línea de Spring Boot elegida son compatibles entre sí; si no lo son, se trata como bloqueo, no como excepción.
- T-B18 (parent POM y `backend/shared-web`) puede simplificar la migración, pero no es prerequisito; si no está hecha, la unificación se hace en el parent existente o en cada `pom.xml`.

### Decisiones tomadas (2026-10-29)

- **Versión destino:** Spring Boot 4.0.9 (Spring Framework 7.0.10, Spring Security 7.0.8, Jackson 3.1.7) y Spring Cloud 2025.1.4 (Gateway 5.0.4). Requiere ADR-018 aprobado y enmienda del Principio X (ver `docs/decisions/adr-018-spring-boot-4.md`).
- **Java:** 21.0.13+9 (Temurin), el build que incluye el parche de CVE-2026-114820 y de la alerta eMudhra. Ninguna versión anterior de Java 21 es válida.
- **Parches incluidos:** CVE-2026-47825 (Spring Cloud Gateway) y los avisos de Spring Framework y Spring Boot de 2026 hasta 7.0.10 / 4.0.9.
