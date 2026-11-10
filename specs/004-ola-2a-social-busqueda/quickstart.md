# Quickstart: validar la Ola 2a

**Feature**: [spec.md](./spec.md) · **Contrato propuesto**: [contracts/ola-2a-operations.md](./contracts/ola-2a-operations.md)

Guía de validación de extremo a extremo. Los detalles de cada operación están en el contrato;
las reglas de datos e índices, en [data-model.md](./data-model.md).

## Requisitos

- Stack local: `docker-compose up -d` (Mongo, Redis, Kafka, gateway, `auth-service`,
  `social-service`, `event-service`). `social-service` con `AUTH_SERVICE_URI` apuntando a
  `auth-service`.
- Usuarios registrados **antes** de desplegar la ola (para SC-002): **Ana** (`ana.r`, "Ana
  Rodríguez") y **José** (`jperez`, "José Pérez").
- Después del despliegue: **Caro** (`caro.mz`, "Carolina Martínez") y **Priv** (`priv.user`, con
  `settings.privateProfile = true` puesto a mano en Mongo, porque no hay pantalla).
- Tokens: `POST /api/v1/auth/login` de cada uno.
- Docker encendido: las pruebas con Testcontainers se saltan sin él; revisar `skipped="0"`.

## 1. Contrato (antes de implementar)

```bash
npm run contract                                      # Spectral + fixtures, en verde
cd frontend/vito_app && flutter test test/contract    # modelos Dart contra los fixtures nuevos
```

Esperado: las 7 operaciones de [la tabla](./contracts/ola-2a-operations.md) con `operationId`,
ejemplos, errores y fixtures; ningún fixture con `email` ni `phone`.

## 2. Búsqueda de usuarios (US1, SC-001, SC-002, SC-006)

1. Como **Ana**, `searchUsers?query=jose` → `200` con `jperez` (sin tilde en la búsqueda, con
   tilde en el nombre). José es anterior a la ola: valida la migración.
2. `searchUsers?query=@caro` → `caro.mz` primero.
3. Como **Caro**, `searchUsers?query=caro` → ella no aparece.
4. `searchUsers?query=priv` → **Priv** no aparece; `getUsersByIds?ids=<id de Priv>` → sí aparece.
5. `searchUsers?query=<correo de José>` → `200 []`.
6. `searchUsers?query=a` y una de 51 caracteres → `400 VALIDATION_ERROR`; `size=21` → `400`.
7. `searchUsers?query=.*` → `200 []` (texto literal, no patrón).
8. 15 búsquedas seguidas como **Ana** → a partir de la 11.ª, `429 RATE_LIMITED` con
   `Retry-After: 1`.
9. App: crear un evento, en "¿A quién invitamos?" escribir "jos", elegir a José e invitar; José ve
   la invitación (SC-001, cierra Bug-F08).

## 3. Seguir y dejar de seguir (US2, SC-004)

1. **Ana** → `followUser` a José → `204`. Repetir → `204`.
2. `getSocialProfile` de José como **Ana** → `followersCount: 1`, `isFollowing: true`,
   `isFollowedBy: false`.
3. **Ana** → `unfollowUser` a José → `204`; repetir → `204`; el contador vuelve a 0.
4. **Ana** → `followUser` a sí misma → `400`; a un id inventado → `404`.
5. Concurrencia (prueba de integración): 1 000 follow y unfollow simultáneos sobre los mismos pares
   → los contadores coinciden con las relaciones y ninguno es negativo.

## 4. Perfil social (US3)

1. `getSocialProfile` de José (usuario anterior a la ola) → nombre y usuario reales (SC-002).
2. José sigue a Ana; `getSocialProfile` de José como **Ana** → `isFollowedBy: true`.
3. `getSocialProfile` de Ana como **Ana** → `isFollowing: false` e `isFollowedBy: false`.
4. Los booleanos se llaman `isFollowing` / `isFollowedBy` en el JSON, no `following` (T-B13).
5. Con `auth-service` detenido: `getSocialProfile` → `503 SERVICE_UNAVAILABLE` con envelope y
   `Retry-After`, nunca `500`.

## 5. Seguidores y seguidos (US4, SC-005)

1. Prueba de integración: 500 usuarios siguen a José; recorrer `listFollowers` con `size=50` →
   cada seguidor exactamente una vez, del más reciente al más antiguo.
2. `listFollowing` de alguien que no sigue a nadie → `200 []`; página más allá del final → `200 []`.
3. `size=51` → `400`; usuario inexistente → `404`.

## 6. App (US2–US4, SC-007)

- "Mi Gente" muestra a quienes sigues y te siguen, sin datos de `MockApiService`, y estados vacíos
  claros.
- El perfil de otra persona permite seguir y dejar de seguir y abre sus listas.
- `grep -rn "MockApiService" lib/features/social lib/features/people` sin resultados en lo social.

## 7. Cierre

- Ninguna respuesta de esta ola trae `email` ni `phone` (prueba automática de SC-006).
- `GET /api/v1/social/search` y `/social/is-following/{id}` responden `404` (retiradas).
- RFC #277 cerrado con el merge del contrato; Bug-F08 (VITO-83) cerrado tras el paso 2.9 en
  producción.
