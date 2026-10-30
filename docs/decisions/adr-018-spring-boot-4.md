# ADR-018: Spring Boot 4.0.x y Spring Framework 7 en el backend Java; Java 21 parcheado

- **Estado:** Propuesta (requiere aprobación de al menos 1 CODEOWNER; ver §Consecuencias)
- **Fecha:** 2026-10-29
- **Decisor:** Lead backend (propuesto); aprobación de un CODEOWNER distinto del autor
- **Resuelve:** T-B31 (excepción A9 de Trivy, vence 2026-11-07) y absorbe T-B10 (O-21, O-35)
- **Relacionado:** Principio X de la constitución, ADR-011 (Fase Tracción), ADR-017 (código
  compartido), spec `specs/002-spring-boot-upgrade/spec.md`

## Contexto

- Los 10 servicios Java usan Spring Boot 3.2.12 (ya sin soporte OSS, O-21) o 3.5.3 en
  algún caso (O-35), sobre Java 17.
- El primer escaneo de Trivy (release 0.1.0) reportó de 39 a 57 CVE HIGH/CRITICAL con
  arreglo por imagen Java. Algunos CRITICAL (`spring-webmvc`, `spring-webflux`) solo se
  arreglan en Spring Framework 7. Desde el 2026-11-08 Trivy vuelve a bloquear el despliegue.
- Principio X fija "Spring Boot 3.x LTS soportado sobre Java 21". Spring Boot 4 queda fuera
  de esa línea, por lo que esta decisión necesita este ADR y una enmienda a la constitución.
- Esta semana Oracle publicó CVE-2026-91403 (alerta fuera de ciclo, 2026-10-21, CVSS 9.1,
  en JSSE, sin autenticación) y CVE-2026-114820 (CVSS 9.1, `java.security`, JDK 8 a 27,
  incluido JDK 21.0.13.0.2). La versión de Java queda fijada por ese parche.

## Decisión

1. **Spring Boot 4.0.9** (última 4.0.x publicada el 2026-10-23) en los 10 servicios, con
   el parent `spring-boot-starter-parent` 4.0.9. Esto trae **Spring Framework 7.0.10**,
   **Spring Security 7.0.8**, **Jackson 3.1.7** y **Testcontainers 2.0.5**, todos gestionados
   por el BOM. No se fijan versiones sueltas de estas librerías.
2. **Java 21.0.13+9 (Eclipse Temurin)** como mínimo en CI, en `backend/Dockerfile` y en
   local. Es el build de Temurin que incluye el parche de CVE-2026-114820 y el de la
   alerta eMudhra. Cualquier build de Java 21 anterior a esa versión queda prohibido.
   El build de Oracle 21.0.13.0.2 sigue afectado; Oracle recomienda 21.0.13.0.3 o superior.
3. **Spring Cloud 2025.1.4** (train Oakwood, compatible con Spring Boot 4.0.x). Incluye
   **Spring Cloud Gateway 5.0.4**, que supera el parche de CVE-2026-47825 (XFF, CVSS 8.6,
   fix en 5.0.2 / 4.3.5 open source). El gateway pasa a `spring-cloud-starter-gateway-server-webflux`.
4. **Parches de seguridad de Spring incluidos:** Spring Framework 7.0.10 incluye los
   avisos de junio (7.0.8) y agosto (7.0.9), entre ellos CVE-2026-41855 (JMS, RCE),
   CVE-2026-41852 (SpEL) y CVE-2026-47893. Spring Boot 4.0.9 incluye los avisos de 4.0.6 a
   4.0.8, entre ellos CVE-2026-40976 (bypass de autorización en actuator, CVSS 9.1, afectaba a 4.0.0 a 4.0.5).
5. **Una sola versión** de Spring Boot, Spring Cloud y Java en los 10 servicios (O-21,
   O-35). Se usa la misma versión en CI, en el Dockerfile y en los poms.

## Alternativas consideradas

- **Spring Boot 3.5.x (última revisión):** queda dentro del Principio X, pero no hay
  garantía de que cubra los CRITICAL de `spring-webmvc` y `spring-webflux`, que solo se
  arreglan en Spring Framework 7. Riesgo alto de que Trivy siga bloqueando después del
  2026-11-08.
- **Mantener la excepción A9:** no es una solución; la excepción vence y el despliegue
  queda bloqueado.

## Consecuencias

- **Enmienda necesaria:** el Principio X debe cambiar de "Spring Boot 3.x LTS" a "Spring
  Boot con soporte OSS vigente" (o la fórmula que aprueben los leads). Sin esa enmienda
  el ADR contradice la constitución.
- **Migración de código:** Jackson 3 (`tools.jackson.*`; las anotaciones siguen en
  `com.fasterxml.jackson.annotation`), `@MockitoBean` en vez de `@MockBean`, starters
  renombrados (`spring-boot-starter-webmvc`), módulos de test separados
  (`spring-boot-resttestclient`, `spring-boot-webmvc-test`, `spring-boot-micrometer-metrics-test`),
  `@AutoConfigureTestRestTemplate`, Kafka con `spring-boot-starter-kafka` y
  `PropertyPlaceholderHelper` sin constructor de 4 argumentos.
- **Riesgo de soporte:** según fuentes secundarias, el soporte OSS de la línea 4.0 termina
  el 2026-12-31. Hay que planificar el siguiente salto de versión menor antes de esa fecha.
  Esto no está verificado contra la página oficial de soporte de Spring.
- **Verificación:** los tests se ejecutan con JDK 21.0.13+9 en los 10 servicios antes del
  merge. Los resultados se pegan en el PR.
