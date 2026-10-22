# ADR-017: Código Java compartido — copia temporal ahora, `shared-web` después

- **Estado:** Aceptada
- **Fecha:** 2026-10-22
- **Decisor:** Ambos leads (se formaliza con la aprobación del PR)
- **Resuelve:** D-05 de `docs/roadmap/decisions-pending.md`; destraba T-B03 (O-29) y fija
  el plan de O-23
- **Relacionado:** ADR-003 (envelope de error), Principio II de la constitución (código
  compartido en `backend/shared-*`), T-I12 (CI por rutas cambiadas)

## Contexto

- `JwtAuthenticationFilter` está copiado en 8 módulos: api-gateway, archive, auth,
  checkin, event, mosaic, social y streaming.
- Solo 3 de los 9 servicios (auth, chat, media) tienen un `@RestControllerAdvice` con el
  envelope de ADR-003. Los otros 6 (social, event, checkin, mosaic, streaming, archive)
  devuelven el error por defecto de Spring y rompen el contrato (O-29).
- Cada servicio se construye aislado: tiene su propio `mvnw` y `pom.xml`, el job `backend`
  de `ci.yml` corre una matriz por carpeta y `backend/Dockerfile` usa la carpeta del
  servicio como contexto. Una librería `backend/shared-web` (Principio II) obliga a cambiar
  las tres cosas a la vez: 1 a 2 días con el CI en riesgo mientras se hace.
- O-29 es un incumplimiento del contrato que el frontend ya sufre; O-23 es deuda interna.
  No conviene que lo segundo retrase lo primero.

## Decisión

1. **Ahora (opción C):** T-B03 copia el `GlobalExceptionHandler` y el `ErrorResponse` de
   `auth-service` a los 6 servicios que no los tienen, adaptando solo el paquete y las
   excepciones de dominio de cada servicio. Así los 9 servicios cumplen ADR-003.
2. **Desviación registrada:** esto contradice el Principio II ("DEBE extraerse… en lugar de
   copiarse"). Se acepta como desviación temporal con esta justificación y fecha de salida;
   el PR de T-B03 la declara en su plantilla y la cita como *Complexity Tracking*.
3. **Después (opción A):** nueva tarea **T-B18**: parent POM Maven multi-módulo en
   `backend/` con el módulo `shared-web` (`JwtAuthenticationFilter`, `ErrorResponse`,
   handler base de ADR-003), `backend/Dockerfile` con contexto `backend/` y el job
   `backend` de CI construido desde el parent. Al terminar se borran todas las copias.
4. **Fecha de salida:** T-B18 pasa a READY cuando se mergee la implementación de la Ola 1,
   y se toma antes de empezar la implementación de la Ola 2.
5. **Tope de la deuda:** mientras T-B18 no esté hecha, no se copia ningún otro tipo de
   código entre servicios. Si aparece una tercera clase compartida (por ejemplo, el sobre
   de eventos Kafka), T-B18 se adelanta en vez de copiar.

## Consecuencias

- **T-B03:** pasa a 🟢 READY. Cierre: los 6 servicios devuelven
  `{error,message,status,timestamp}`, con tests MockMvc de 400, 404 y 500 en cada servicio.
- **T-B18:** se agrega al backlog en estado 🔴 BLOQUEADA (implementación de la Ola 1). Toca
  `backend/pom.xml` (nuevo), los `pom.xml` de los servicios, `backend/Dockerfile`,
  `ci.yml` (jobs `backend` y `docker`) y la matriz de `cd.yml`. Debe coordinarse con T-I12,
  porque los filtros `paths` tienen que incluir `backend/shared-web/**` en todos los
  servicios.
- **Riesgo aceptado:** hasta T-B18, un cambio en el handler o en el filtro JWT hay que
  repetirlo en cada copia. Para que no se desincronicen, un cambio en una copia sin el
  mismo cambio en las demás se rechaza en review.
- **Constitución:** no se enmienda. El Principio II sigue igual; esta es una desviación con
  fecha de salida, no un cambio de regla. O-23 se cierra con T-B18, O-29 con T-B03.

## Alternativas consideradas

| Alternativa | Por qué no |
|-------------|------------|
| A ya: parent POM antes de T-B03 | Deja O-29 abierto 1 a 2 días más, con el CI y el Dockerfile en cambio al mismo tiempo que otras tareas del backend. |
| B: librería en GitHub Packages | Agrega versionado, publicación y autenticación del registro en el CI y en Docker para un repo único donde un módulo Maven resuelve lo mismo. |
| Solo C, sin fecha de salida | La copia se vuelve permanente y el Principio II deja de cumplirse sin que nadie lo decida. |
