# Decisiones pendientes (D-xx)

Cada decisión la toman los leads. Se cierra con un ADR (`docs/decisions/adr-NNN-*.md`,
rama `contract/<nombre>` si afecta el contrato, `docs/<nombre>` si no) y se marca aquí
como ✅ con el número de ADR. Las tareas bloqueadas en `tasks.md` citan el D-xx.

Las opciones listadas son las que salieron del análisis del 2026-10-16. **La columna
"Propuesta" es una recomendación, no una decisión.**

| ID | Decisión | Bloquea | Propuesta | Estado |
|----|----------|---------|-----------|--------|
| D-01 | Dueño de cada dominio duplicado | Olas 1–4 | ver §D-01 | ✅ [ADR-016](../decisions/adr-016-domain-owners.md) (propuesta completa; invitación vs RSVP se fija en la Ola 1) |
| D-02 | Stack de despliegue y herramientas por costo | Etapa 5, O-11/12/13/17 | ver §D-02 | ✅ [ADR-011](../decisions/adr-011-traction-stack.md) |
| D-03 | Semántica del listado de eventos (`GET /events`) | T-C06, T-B07, T-F08 | ver §D-03 | ✅ [ADR-014](../decisions/adr-014-events-listing-scope.md) (opción A, `scope=mine\|upcoming`) |
| D-04 | Mensajería durable del MVP (Kafka vs alternativa) | Ola 3, O-16/O-17 | ver §D-02.b | ✅ ADR-011 (Kafka KRaft, 1 broker) |
| D-05 | Estrategia de código compartido Java (`shared-*`) | T-B03, O-23 | ver §D-05 | ✅ [ADR-017](../decisions/adr-017-shared-java-code.md) (C ahora; A en T-B18 tras la Ola 1) |
| D-06 | Identidad en el WS de chat (`?userId=`) | T-B06, T-F04 (parte chat) | ver §D-06 | ✅ [ADR-015](../decisions/adr-015-chat-ws-identity.md) (se retira `userId`; identidad de `X-User-Id`) |
| D-07 | Estrategia de ramas (`develop` vs `main`) | O-24, **T-I08 (deploy)** | ver §D-07 | ✅ [ADR-012](../decisions/adr-012-branching-and-release.md) |
| D-08 | Aprobación del triage de `endpoint-inventory.md` | Olas 1–6 | aprobar por ola | ⏳ |
| D-09 | Titularidad de cuentas y medio de pago de la infraestructura | T-I06 (aprovisionar) | ver §D-09 | ✅ [ADR-013](../decisions/adr-013-account-ownership.md) |
| D-10 | Tipo de cuenta de Google Play (personal u organización) | Publicación Android | ver §D-10 | ⏳ **prioritaria** · pospuesta el 2026-10-22 |
| D-11 | Modelo de monetización (qué se cobra y a quién) | Ola de pagos (nueva), primeros ingresos | ver §D-11 | ⏳ pospuesta el 2026-10-22 |
| D-12 | Política de privacidad, términos y transferencia de datos (Ley 1581) | Publicación en tiendas, lanzamiento | ver §D-12 | ⏳ |

---

## D-01 — Dueño de dominios duplicados

> ✅ **Cerrada el 2026-10-22 por [ADR-016](../decisions/adr-016-domain-owners.md):** se
> adopta la propuesta completa. La fila invitación vs RSVP se resuelve en la spec de la
> Ola 1. La tabla de abajo se conserva como historial.

Hoy la misma capacidad vive en dos servicios:

| Capacidad | Implementación A | Implementación B | Propuesta |
|-----------|------------------|------------------|-----------|
| QR + check-in | `event-service`: `POST /events/{id}/check-in`, `GET /events/{id}/qr-code` | `checkin-service`: `/checkin/ticket`, `/checkin/validate`, stats, recent | **checkin-service** es dueño. Se retiran las dos rutas de event-service. checkin consulta capacidad/estado del evento por API o evento Kafka. |
| Subida de medios | `media-service`: `POST /media/upload` (en contrato) | `mosaic-service`: `POST /mosaic/photo` | **media-service** sube y guarda; mosaic solo crea el ítem con `mediaId` (`POST /mosaic/post`). Se retira `/mosaic/photo`. |
| Ítem de check-in en el mosaico | `mosaic-service`: `POST /mosaic/checkin` (público) | — | **Interno**: lo crea mosaic al consumir el evento de check-in. No se enruta en gateway. |
| Chat | `chat-service` (salas, mensajes) | `streaming-service`: `POST/GET /streaming/{id}/chat` | **chat-service** es dueño; el stream usa una sala de chat ligada al `streamId`. Se retira el chat de streaming. |
| Info de stream | `GET /streaming/{id}` | `GET /streaming/{id}/info` | Fusionar en `GET /streaming/{id}`. |
| Invitación vs RSVP | `POST /events/{id}/invitations/respond` | `POST /events/{id}/rsvp` | Mantener ambos solo si la semántica difiere (aceptar invitación privada vs RSVP público). Definir en Ola 1. |

## D-02 — Stack de despliegue y costo

> ✅ **Cerrada el 2026-10-17 por [ADR-011](../decisions/adr-011-traction-stack.md)** (Fase
> Tracción, 100.000 COP/mes). Las opciones de abajo se conservan como historial.

La constitución v1.1.0 marca estas reglas como **en revisión** (no se implementan ni se
eliminan hasta cerrar esta decisión). Sub-decisiones:

| # | Hoy exige | Alternativas a evaluar |
|---|-----------|------------------------|
| a | Kubernetes + ArgoCD + Kustomize overlays dev/staging/prod, HPA, PDB, NetworkPolicy | VM única con Docker Compose (staging) · k3s en 1 nodo · k8s gestionado solo para prod post-MVP |
| b | Kafka KRaft, replicación ≥ 3 | 1 broker KRaft en staging · Redpanda · Redis Streams para el MVP (Kafka después) |
| c | MongoDB Atlas replica set + backups diarios probados | Atlas M0/Flex en staging · Mongo autoalojado + `mongodump` programado |
| d | 10 microservicios Java + 3 Python desplegables | Mantener · Fusionar por afinidad (event+checkin, media+mosaic+archive) según Principio X |
| e | AWS S3 / MinIO (compose ya usa **SeaweedFS**) | SeaweedFS · MinIO · Cloudflare R2 (sin egress) · S3 |
| f | Redis-compatible: AGENTS dice Dragonfly, código usa Redis 7.2 | Redis · Dragonfly · Valkey |
| g | Service discovery: AGENTS dice Eureka, código usa rutas estáticas | DNS de la plataforma (sin Eureka) · Eureka |
| h | Prometheus + Grafana + OTel con dashboards y alertas como código | Grafana Cloud free tier · stack autoalojado mínimo |
| i | Gestor de secretos (Sealed Secrets / AWS Secrets Manager) | GitHub Environments secrets · Sealed Secrets · Doppler free |
| j | Registro de imágenes GHCR (plan Free: cuota de almacenamiento privado) | GHCR con retención · Docker Hub · registro del proveedor cloud |
| k | Streaming SRS / Ant Media | SRS autoalojado 1 nodo (Ant Media fuera del MVP) |

**Entrada requerida de los leads:** presupuesto mensual objetivo para staging y para
producción del MVP.

## D-03 — Listado de eventos

> ✅ **Cerrada el 2026-10-19 por [ADR-014](../decisions/adr-014-events-listing-scope.md):**
> opción A con `scope=mine|upcoming` (sin `public`) y `mine` por defecto. Las opciones de
> abajo se conservan como historial.

El contrato publica `GET /api/v1/events` (`listEvents`) y el frontend lo usa como
"mis eventos" (`event_remote_datasource.dart:147`). El backend no lo implementa: solo
tiene `GET /events/my-events` y `GET /events/upcoming` → hoy responde 405.

| Opción | Efecto |
|--------|--------|
| A (propuesta) | `GET /events?scope=mine\|upcoming\|public&page&size`. Backend fusiona `/my-events` y `/upcoming` en uno. Front cambia `queryParameters`. |
| B | Contrato pasa a `GET /events/my-events` y `GET /events/upcoming`; se retira `listEvents`. |

## D-05 — Código compartido Java

> ✅ **Cerrada el 2026-10-22 por [ADR-017](../decisions/adr-017-shared-java-code.md):**
> opción C ahora (T-B03 copia el handler de auth-service, como desviación registrada del
> Principio II) y opción A en T-B18, que se toma antes de implementar la Ola 2. Las
> opciones de abajo se conservan como historial.

La constitución (Principio II) exige extraer a librería `backend/shared-*` el
`JwtAuthenticationFilter` (duplicado en 7 servicios) y el `ErrorResponse`/handler de
ADR-003. Hoy cada servicio se construye aislado (contexto Docker = carpeta del servicio,
sin parent POM).

| Opción | Efecto |
|--------|--------|
| A (propuesta) | Parent POM Maven multi-módulo en `backend/` con `shared-web`; Dockerfile con contexto `backend/`. Cambia CI y `backend/Dockerfile`. |
| B | Librería publicada en GitHub Packages; cada servicio la consume por versión. |
| C (temporal) | Copiar el handler de auth-service a los 6 servicios que faltan y registrar la deuda en *Complexity Tracking*. |

## D-06 — Identidad en WS de chat

> ✅ **Cerrada el 2026-10-20 por [ADR-015](../decisions/adr-015-chat-ws-identity.md):**
> se adopta la propuesta. La ruta queda `/ws/chat?token=<jwt>` y el servicio toma la
> identidad de `X-User-Id`. El texto de abajo se conserva como historial.

`CONVENTIONS.md` §7 publica `/ws/chat?userId=<id>&token=<jwt>`. El `userId` en query lo
elige el cliente (suplantable) y es redundante con el token. **Propuesta:** retirar
`userId`; el servidor toma la identidad del token validado por el gateway. Cambio de
contrato → RFC.

## D-07 — Ramas

> ✅ **Cerrada el 2026-10-17 por [ADR-012](../decisions/adr-012-branching-and-release.md):**
> PR de feature a `develop`; release semanal (viernes) de `develop` a `main` con merge
> commit; esa aprobación es la aprobación de producción.

> **Impacto de ADR-011:** el despliegue a producción sale de `main`, y la aprobación
> manual es la aprobación del PR que entra a `main`. Esta decisión define si ese PR es un
> release semanal desde `develop` (propuesta) o cada PR de feature directo a `main`.

`CONTRIBUTING.md` define `develop` como integración, pero los PR recientes (#12, #13)
van a `main` y `deploy-frontend.yml` despliega desde `main`. **Propuesta:** mantener
`develop` como destino de PR de feature y promover a `main` con un PR de release
semanal (viernes), que dispara el deploy.

## D-08 — Triage de endpoints

Aprobar (o corregir) la columna "Decisión propuesta" de `endpoint-inventory.md`, **ola
por ola**. Aprobar la ola N desbloquea su rama `contract/<ola>`.

## D-09 — Titularidad de cuentas y medio de pago

> ✅ **Cerrada el 2026-10-17 por [ADR-013](../decisions/adr-013-account-ownership.md).**

ADR-011 requiere abrir cuentas en Contabo, Cloudflare (R2, Pages, dominio), Grafana Cloud,
Brevo/Resend y Google Play, y pagarlas con tarjeta en moneda extranjera.

| Pregunta | Propuesta |
|----------|-----------|
| ¿A nombre de quién? | Un correo de equipo (p. ej. `infra@<dominio>`) como dueño de cada cuenta, **no** el personal de un lead. Ambos leads como administradores donde el proveedor lo permita. |
| ¿Con qué tarjeta? | Una sola tarjeta (idealmente virtual o de la futura empresa), con el gasto registrado mes a mes en una hoja compartida. |
| ¿Credenciales? | Gestor de contraseñas compartido con 2FA obligatorio; códigos de recuperación guardados fuera del gestor. |
| ¿Quién recibe alertas de cobro y de caída? | Ambos leads. |

## D-10 — Cuenta de Google Play: personal u organización

| Opción | Efecto |
|--------|--------|
| Personal | Se abre rápido. Las cuentas personales nuevas deben hacer una **prueba cerrada con al menos 12 testers durante 14 días** antes de publicar en producción (requisito de Google; verificar vigente). Queda a nombre de una persona. |
| Organización | Requiere número **D-U-N-S** de una empresa constituida; sin el requisito de testers. La app queda a nombre de la empresa. |

> **Revisión del 2026-10-22 (fuentes de terceros; confirmar en la ayuda de Play Console):**
> aplica a cuentas personales creadas después del 13-nov-2023; si los testers inscritos
> bajan de 12, el conteo de 14 días vuelve a cero, y Google revisaría que la app se use de
> verdad. Son unas 3 semanas en la ruta crítica desde el primer build en prueba cerrada.

**Propuesta:** si la empresa no está constituida, cuenta personal de un lead y **empezar ya
la prueba cerrada** con 12+ testers (amigos, primeros organizadores), porque esos 14 días
están en la ruta crítica del lanzamiento Android. Migrar la app a una cuenta de organización
cuando exista la empresa.

## D-11 — Modelo de monetización

El roadmap actual no tiene ninguna funcionalidad de pago: ni endpoints en el contrato ni
pantallas. Sin esta decisión no hay "primeros ingresos" posibles.

| Opción | Ejemplo | Efecto técnico |
|--------|---------|----------------|
| A. Plan para organizadores (freemium) | Gratis hasta N invitados; plan pago por evento con streaming, mosaico ampliado y más invitados | Flags por plan en `event-service` + checkout por evento |
| B. Pago por evento premium | Pago único al publicar un evento con funciones premium | Igual que A, sin suscripción |
| C. Entradas pagas (ticketing) | El organizador cobra la entrada; ViTo toma una comisión | Split de pagos en la pasarela, conciliación, más obligaciones legales y fiscales |
| D. Suscripción de usuarios | Suscripción mensual | Billing recurrente; si se cobra dentro de la app Android aplica Google Play Billing y su comisión |

**Propuesta:** **A o B con checkout alojado de Wompi o Mercado Pago, pagado desde la web.**
Es lo más simple, la app nunca toca datos de tarjeta y, si el pago se hace en la web (no
dentro de la app Android), en principio no aplica la comisión de Google Play. Verificar
con las políticas vigentes de Google Play antes de implementar.

> **Corrección del 2026-10-22:** que el pago sea en la web no basta. La política de pagos
> de Google Play prohíbe que la app enlace o dirija al usuario a un pago externo para
> funciones digitales (incluido un enlace a una web que lleve al pago); fuera de la app sí
> se puede ofrecer. Los programas de enlace externo (EE. UU., Reino Unido y EEE desde el
> 30-jun-2026) cobran comisión, y no está confirmado que cubran a Colombia. Con A o B, la
> app solo refleja el plan comprado, sin botón ni enlace de compra. La opción C (entradas
> a un evento presencial) es un servicio físico y no usa Google Play Billing. Implica una nueva ola de
contrato ("Ola P: pagos").

## D-12 — Privacidad, términos y transferencia de datos

ADR-011 aloja los datos en EE. UU. (Contabo) y en Cloudflare. El Principio IX exige
cumplir la Ley 1581 de 2012. Google Play exige una URL de política de privacidad para
publicar.

| Pregunta | Propuesta |
|----------|-----------|
| Política de privacidad y términos | Redactar y publicar en la web antes de la prueba cerrada de Google Play. Revisión por un abogado cuando haya ingresos. |
| Transferencia internacional | Verificar que EE. UU. esté en la lista de países con nivel adecuado de protección de la SIC o, si no, obtener autorización expresa del titular. Declararlo en la política. |
| Responsable del tratamiento | Persona natural (lead) o la empresa, según D-09 y D-10. |
| Borrado de cuenta | Google Play exige un mecanismo de eliminación de cuenta; coincide con el flujo `USER_DELETED` del Principio IX. Planificarlo en la Ola 1. |
| Insumos del triage de seguridad (T051) | M4 (cifrado en reposo: excepción en Fase Tracción), M5 (qué dato es sensible y si se cifra por campo), M10 (Ley 1581 en lugar de GDPR/CCPA) y M11 (retención, exportación y borrado) de `backend/SECURITY_CHECKLIST.md` se resuelven con esta decisión. |
