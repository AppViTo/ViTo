---
name: Change de contrato (RFC)
about: Proponer un cambio en docs/api/openapi.yaml o asyncapi.yaml
title: "[contract] "
labels: contract
---

<!--
PROCESO OBLIGATORIO
1. Rellenar este RFC.
2. Discusión en este issue.
3. Si se aprueba: rama contract/<nombre> → PR con el diff del contrato
   (+ fixtures + ADR si aplica) → requiere aprobación de al menos 1 lead (CODEOWNER).
4. Los equipos actualizan su código en el mismo sprint.
Nunca se implementa primero y se documenta después.
-->

## 1. Quién lo pide

- **Equipo:** frontend / backend
- **Feature afectada:**

## 2. Problema

<!-- Qué hace falta que hoy no está en el contrato, o qué parte del contrato
     actual no sirve. Sé concreto: path, schema, campo. -->

## 3. Propuesta

```yaml
# Dif del contrato (OpenAPI o AsyncAPI)
```

**Método + path:**
**Request (ejemplo completo):**
```json
{}
```
**Response 200/201 (ejemplo completo):**
```json
{}
```
**Errores posibles:** <!-- código → cuándo ocurre -->

## 4. Alternativas consideradas

<!-- Qué otra solución se evaluó y por qué se descartó. -->

## 5. Impacto

| Área | Impacto |
|------|---------|
| Frontend (archivos) | |
| Backend (servicios) | |
| Contrato (archivos) | |
| Mocks/fixtures | |
| ¿Rompe compatibilidad? | sí / no |

## 6. Justificación

<!-- Por qué conviene cambiar el contrato en vez de adaptar el código. -->

## 7. Aprobaciones

- [ ] Frontend lead
- [ ] Backend lead
- [ ] ADR creado en `docs/decisions/` (si es una decisión nueva)

---
*Aprobado → rama `contract/<nombre>` → PR a `develop`.*
