## Descripción

<!-- ¿Qué hace este PR y por qué? 2-3 líneas. -->

## Tipo de cambio

- [ ] `feat` — nueva funcionalidad
- [ ] `fix` — corrección de bug
- [ ] **`contract` — cambio al contrato de API (docs/api/**)**
- [ ] `docs` — documentación
- [ ] `refactor` / `test` / `ci` / `chore`

## ¿Altera el contrato de API?

> Obligatorio: si marcas "Sí" y no incluye el diff del contrato, el PR se cierra.

- [ ] **No.** El cambio es solo de frontend/backend y respeta el contrato existente.
- [ ] **Sí**, y adjunto lo siguiente:

  - [ ] Diff de `docs/api/openapi.yaml` y/o `docs/api/asyncapi.yaml`
  - [ ] Fixtures actualizados o creados en `docs/api/fixtures/`
  - [ ] ADR nuevo en `docs/decisions/adr-NNN-*.md` (si es una decisión)
  - [ ] RFC (issue con template `contract-change`) enlazado: #
  - [ ] El equipo afectado fue notificado

## Checklist según el área tocada

**Frontend (`frontend/**`)**
- [ ] `flutter analyze --fatal-infos` sin errores
- [ ] `flutter test` verde
- [ ] Sin `print()` en producción
- [ ] Sin mocks/TODOs en el flujo de la feature (o están etiquetados)
- [ ] Los `*Model.fromJson` siguen el contrato (tests de serialización verdes)

**Contrato (`docs/api/**`, `docs/decisions/**`)**
- [ ] `npx @stoplight/spectral-cli lint docs/api/openapi.yaml` sin errores
- [ ] `npx @asyncapi/cli validate docs/api/asyncapi.yaml` (si aplica)
- [ ] Toda operación nueva tiene `operationId`, ejemplos y `ErrorResponse`
- [ ] Fixture presente para cada operación nueva

**Backend (`backend/**`, `ai-services/**`)**
- [ ] `mvn clean test` (o `pytest`) verde
- [ ] El endpoint coincide **exactamente** con el contrato (paths, enums, envelop de lista, códigos HTTP)
- [ ] `schemathesis run --checks all http://localhost:8080/api/v1/openapi.json` verde (si el servicio está levantado)
- [ ] No se implementó ningún endpoint ausente del contrato

**CI / Infra (`.github/workflows/**`, `infrastructure/**`)**
- [ ] Los jobs afectados pasan en este PR
- [ ] No se rompe el pipeline existente

## Testing realizado

<!-- ¿Cómo lo verificaste? Comandos, pantallas, endpoints probados. -->

## Impacto / riesgo

<!-- ¿Qué puede romperse? ¿Requiere coordinación con el otro equipo? -->

## Revisión

- [ ] He leído `CONTRIBUTING.md`
- [ ] Aprobación del CODEOWNERS de las rutas afectadas
