# Fixtures del contrato

Ejemplos ejecutables de [`../openapi.yaml`](../openapi.yaml). **Un solo origen de
verdad:** los mismos archivos alimentan el mock Prism (`npm run mock:api`), la
validación de CI y los tests Dart de serialización (T-C04). Si cambia el
contrato, cambian los fixtures; los dos lados se enteran en CI.

> Esta convención es **vinculante para la herramienta que los lea**: T-C03
> (validación en CI) y T-C04 (tests Dart) resuelven los fixtures **por nombre**,
> no por índice. No existe un manifiesto que mantener en paralelo.

## Convención de nombres

```
docs/api/fixtures/<tag-en-kebab>/<operationId>.<extensión>.json
```

| Parte | Regla |
|-------|-------|
| `<tag-en-kebab>` | `tags[0]` de la operación, en minúsculas: `authentication`, `events`, `chat`, `media`, `recommendations`, `moderation`. |
| `<operationId>` | Tal cual figura en el YAML. Es el identificador único de la operación. |
| `<extensión>` | `request` = cuerpo del request. Un código HTTP (`200`, `201`, `400`, `401`, `404`, `409`, `429`) = cuerpo de la respuesta de ese status. |

Ejemplos:

```
authentication/registerUser.request.json    → body de POST /api/v1/auth/register
authentication/registerUser.201.json        → respuesta 201 de la misma operación
authentication/registerUser.409.json        → respuesta 409 (CONFLICT)
events/listEvents.200.json                  → array plano (ADR-002)
```

## Reglas

1. **Una carpeta por tag**, no por servicio: el consumidor del fixture es el
   contrato, no el código.
2. **Cada operación con `requestBody` tiene un `.request.json`**, salvo
   `multipart/form-data` (ver §Multipart abajo).
3. **Cada status declarado en `responses` tiene su `.<status>.json`**, incluidos
   los 4xx/5xx. No se inventan statuses que el YAML no declare.
4. **Un fixture nunca contradice su schema.** Si el ejemplo y el schema
   discrepan, se corrige el fixture. Si el schema parece mal, se abre un RFC
   (`contract-change`); el fixture no se "arregla" para tapar el problema.
5. **Los errores usan el envelope de ADR-003** (`error`, `message`, `status`,
   `timestamp`, `details?`) con código canónico según el status:
   `400 → VALIDATION_ERROR`, `401 → UNAUTHORIZED`, `403 → FORBIDDEN`,
   `404 → NOT_FOUND`, `409 → CONFLICT`, `429 → RATE_LIMITED`.
   `message` va en **es-ES** y es específico de la operación, no genérico.
6. **Los campos opcionales se ejercitan**: al menos un fixture 2xx de cada
   recurso importante incluye los campos opcionales (p. ej. `Media.metadata`,
   `ChatMessage.mediaUrl`, `Event.details.coverImages`), para que los modelos
   Dart se prueben contra el caso completo y no solo contra el mínimo.
7. **Los datos son coherentes entre fixtures** (mismos `usr_*`, `evt_*`,
   `room_*`, fechas ISO-8601 UTC con `Z`): un test que cargue varios fixtures
   no debe encontrar identidades contradictorias.

## Respuestas sin cuerpo

Cuando el YAML declara un status **sin `content`** (p. ej. `logoutUser.200`),
el fixture existe y contiene el literal JSON `null`. Así se mantiene la regla
«todo status declarado tiene fixture» y el validador sabe, por el propio YAML,
que ahí **no** hay schema que aplicar.

```
authentication/logoutUser.200.json   → null
```

## Requests multipart

`uploadMedia` (`POST /api/v1/media/upload`) declara `multipart/form-data` con un
campo binario. **No se genera `.request.json`** para ella: un JSON no puede
representar el `file` binario. El validador solo aplica schema a las respuestas
de esa operación. El `eventId` que acompaña al multipart sí queda documentado en
el `description` del YAML (CONVENTIONS §8).

## Cobertura actual (Ola 0)

Las **15 operaciones** de `openapi.yaml` están cubiertas: 8 requests (las 7 con
body JSON + los casos ya citados) y 45 responses. Verificación:

```bash
# que cada operationId del YAML tenga al menos un 2xx y un 4xx
# y que cada fixture valide contra su schema → T-C03 (job "contract" de CI)
npm run lint:api
```

## Cómo añadir un fixture

1. El endpoint debe estar **ya** en `openapi.yaml` (contrato primero).
2. Crea el archivo con el nombre de arriba, copiando el `example` del YAML si lo
   tiene y enriqueciéndolo con los campos opcionales que el schema permite.
3. Ejecuta la validación y adjunta la salida al PR.
