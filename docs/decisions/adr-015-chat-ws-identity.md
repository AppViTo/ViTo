# ADR-015: Identidad en el WebSocket de chat sin `?userId=`

- **Estado:** Aceptada
- **Fecha:** 2026-10-20
- **Decisor:** Ambos leads (se formaliza con la aprobación del PR)
- **Resuelve:** D-06 de `docs/roadmap/decisions-pending.md` y la parte de identidad de O-28
- **Relacionado:** ADR-006 (WS crudo, `?token=`, cierre `4401`), ADR-007 (`X-User-*`
  inyectados por el gateway), T-B04 (validación de `?token=` en el gateway)

## Contexto

ADR-006 y `CONVENTIONS.md` §7 publican la ruta del chat como
`/ws/chat?userId=<id>&token=<jwt>`. Las otras tres rutas WS solo llevan `?token=`.

- El `userId` lo elige el cliente. Cualquiera puede conectar con el id de otra persona y,
  si el servicio confía en ese valor, leer sus salas y escribir en su nombre
  (suplantación, Principio III).
- Es redundante: desde T-B04 el gateway valida `?token=`, inyecta `X-User-Id` con el
  `sub` del JWT y quita el token de la URL antes de pasar el handshake al servicio.
- El cliente actual (`chat_remote_datasource.dart`) conecta con `?userId=` y **sin**
  `?token=`, y el `ChatWebSocketHandler` lee la identidad de un atributo de sesión que
  nadie llena. Ninguno de los dos funciona hoy, así que el cambio no rompe nada en uso.

## Decisión

1. **Ruta del chat:** `/ws/chat?token=<jwt>`. Se retira `userId` del contrato, igual que en
   las otras tres rutas WS.
2. **Identidad solo del gateway:** el servicio toma al usuario del header `X-User-Id` del
   handshake (ADR-007), lo guarda en la sesión WS al conectar y lo usa en todos los frames.
3. **Nunca del cliente:** el servicio ignora `userId` en la query y cualquier
   `userId`/`senderId` dentro de los frames. Si un cliente viejo sigue enviando `?userId=`,
   no tiene efecto.
4. **Sin identidad, sin sesión:** un handshake que llega sin `X-User-Id` se cierra con
   `4401` (el mismo código de ADR-006). En la práctica solo pasa si alguien llama al
   servicio sin pasar por el gateway.
5. **Autorización por sala:** `JOIN_ROOM`, `SEND_MESSAGE`, `TYPING` y `MARK_READ` verifican
   que el usuario de la sesión participe en la sala, con la misma regla que las rutas REST
   de `/chat/rooms/{roomId}`. Sin esto la identidad autenticada no protege nada.
6. **Regla general:** ninguna ruta WS o REST acepta la identidad del usuario como parámetro
   o campo enviado por el cliente. Coincide con ADR-014 (`listEvents` sin `organizerId`).

## Consecuencias

- **Contrato:** `CONVENTIONS.md` §7 y ADR-006 publican `/ws/chat?token=<jwt>`.
  `asyncapi.yaml` (T-C05) documenta el canal de chat sin `userId`.
- **Backend (T-B06):** al pasar `chat-service` a WS crudo se registra un
  `HandshakeInterceptor` que copia `X-User-Id` a los atributos de la sesión y cierra con
  `4401` si falta, más la verificación de participante por frame. T-B06 queda bloqueada
  solo por T-C05.
- **Frontend (T-F04):** el chat entra en T-F04 con las otras tres rutas: URL desde
  `Env.wsBaseUrl`, `?token=<accessToken>` y sin `?userId=`; `connect` deja de recibir el
  `userId`.
- El resto de WS (checkin, mosaic, streaming) ya sigue esta regla; si leen identidad, la
  leen de `X-User-Id`.

## Alternativas consideradas

| Alternativa | Por qué no |
|-------------|------------|
| Mantener `?userId=` y que el gateway o el servicio rechacen el handshake si no coincide con el `sub` del token | Más código y más casos de error a cambio de un dato que el servidor ya tiene. El contrato seguiría sugiriendo que el cliente decide su identidad. |
| Mantener `?userId=` solo como dato informativo | Invita a que algún servicio lo use más adelante; es la puerta exacta de la suplantación. |
| Enviar la identidad en un frame `AUTH` después de conectar | Introduce un estado "conectado sin autenticar" y un protocolo distinto al de las otras tres rutas, que ya autentican en el handshake (ADR-006). |
