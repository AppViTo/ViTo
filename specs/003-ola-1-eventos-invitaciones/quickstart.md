# Quickstart: validar la Ola 1

**Feature**: [spec.md](./spec.md) · **Contrato propuesto**: [contracts/ola-1-operations.md](./contracts/ola-1-operations.md)

Guía de validación de extremo a extremo. Los detalles de cada operación están en el contrato;
los estados y transiciones, en [data-model.md](./data-model.md).

## Requisitos

- Stack local: `docker-compose up -d` (Mongo, Redis, Kafka, gateway, `auth-service`,
  `event-service`), con `INVITE_LINK_BASE_URL=http://localhost:8080` en el `.env` local.
- Dos usuarios registrados: **Org** (organizador) e **Inv** (invitado), y un tercero, **Otro**.
- Tokens: `POST /api/v1/auth/login` de cada uno.

## 1. Contrato (antes de implementar)

```bash
npm run contract          # Spectral + validación de fixtures; debe quedar en verde
cd frontend/vito_app && flutter test test/contract   # modelos Dart contra los fixtures nuevos
```

Esperado: las 13 operaciones de [la tabla](./contracts/ola-1-operations.md#operaciones) con
`operationId`, ejemplos y errores; ningún fixture inválido.

## 2. Enlace de invitación (US1, SC-001, SC-002, SC-006)

1. **Org** crea un evento público con cupo 2 y llama a `createInviteLink` sin cuerpo →
   `201`, `expiresAt` a 7 días, `uses: 0`, `url` que empieza por `INVITE_LINK_BASE_URL/i/`.
2. Sin `Authorization`, `getInviteLinkPreview` con ese token → `200`, sin dirección exacta ni
   coordenadas aunque el evento no la oculte.
3. Con un token inventado, con uno revocado y con uno vencido → los tres `404` con el mismo
   cuerpo (comparar `error` y `message`).
4. **Inv** llama a `acceptInviteLink` → `200` `ACCEPTED`; repetir → mismo `200`, `uses` sigue en 1.
5. **Org** llama a `acceptInviteLink` con su propio enlace → `200` sin cambios.
6. `listEvents?scope=invited` como **Inv** → contiene el evento.
7. En la web, abrir `/i/<token>` sin sesión → vista previa; registrarse → vuelve a la
   invitación y queda confirmado.

## 3. RSVP, cupo y lista de espera (US3, SC-003)

1. Con cupo 2, tres usuarios responden `ACCEPTED` → dos `ACCEPTED`, uno `WAITLISTED`.
2. Un confirmado responde `DECLINED` → el `WAITLISTED` pasa a `ACCEPTED`.
3. Repetir la misma respuesta → mismo resultado; `stats.totalAccepted` no cambia.
4. Evento privado: **Otro**, sin invitación, responde → `404` igual al de un evento inexistente.
5. Concurrencia (prueba de integración con Testcontainers): 50 respuestas simultáneas con
   cupo 20 → exactamente 20 `ACCEPTED` y 30 `WAITLISTED`, en 10 ejecuciones seguidas.

```bash
cd backend && ./mvnw -pl event-service -am test   # incluye la prueba de concurrencia
```

## 4. Editar y cancelar (US2, US5, SC-007)

1. **Org** hace `PATCH` con solo `details.description` → `200`, el resto intacto.
2. Bajar `maxAttendees` por debajo de los confirmados → `409`.
3. **Otro** hace `PATCH` → `403`. Enviar `stats` o `status` → `400`.
4. **Org** cancela (`DELETE`) → `204`; repetir → `204`. El evento sale de `upcoming` e
   `invited`; responder RSVP o unirse por enlace → `409`; `PATCH` → `409`.

## 5. Invitaciones por la app y asistentes (US4, US6, SC-005, SC-005a)

1. **Org** invita a **Inv** → `201 PENDING`; repetir → `409`.
2. **Otro** llama a `listEventInvitations` y `listEventAttendees` → `403`.
3. **Inv** confirmado llama a `listEventAttendees` → `403`; en `getEvent` ve `stats` con solo
   `totalAccepted`, igual al número de `ACCEPTED` de la lista del organizador.
4. **Org** en `getEvent` ve `stats` completo.

## 6. Sesión (US7)

`GET /api/v1/auth/me` con token válido → `200` con la misma forma que `AuthResponse.user`;
con token revocado (`logout`) → `401`.

## 7. Carga (Principio VII, SC-008)

```bash
k6 run backend/load-tests/ola-1-rsvp.js      # 150 asistentes, RSVP concurrente
k6 run backend/load-tests/ola-1-preview.js   # vista previa del enlace
```

Esperado: RSVP y unión < 1 s p95; vista previa y lecturas < 300 ms p95; 0 cupos superados.
