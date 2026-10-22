# Feature Specification: Despliegue continuo a producción (Fase Tracción)

**Feature Branch**: `docs/001-cd-produccion`

**Created**: 2026-10-22

**Status**: Draft

**Input**: User description: "Despliegue continuo a producción (Fase Tracción, ADR-011). Cierra T-I08 (pipeline de despliegue al VPS por SSH), T-I11 (frontend web en Cloudflare Pages) y T-I13 (observabilidad con Grafana Cloud), y completa lo que falta de T-I06. Contexto: ya hay VPS Contabo 12 GB en EE. UU., buckets R2, cuenta de Grafana Cloud y correo de equipo en Gmail; faltan la llave SSH, el Environment `production` en GitHub (con revisores obligatorios: constitución v1.3.0) y configurar Resend. Ya existen docker-compose.prod.yml, Caddyfile, .env.prod.example y el backup a R2 (T-I07, T-I09, T-I10). Al mergear un release a main (ADR-012), el sistema construye las imágenes de los servicios cambiados (tag = SHA), las copia al VPS con docker save | ssh docker load, despliega con docker compose, verifica los healthchecks y permite rollback manual a un SHA anterior. La web Flutter se publica en Cloudflare Pages en lugar de Vercel. El VPS envía métricas, logs y trazas a Grafana Cloud, con alertas de caída y de recursos a ambos leads. Problema conocido: docker-compose.prod.yml usa un único IMAGE_TAG para todos los servicios, lo que choca con construir solo los servicios cambiados."

**Cierra:** T-I08, T-I11, T-I13 y el resto de T-I06 · **Observaciones:** O-12, O-15 (parte de despliegue) ·
**Marco:** ADR-011 (Fase Tracción), ADR-012 (release semanal), ADR-013 (cuentas),
constitución v1.3.0 (Principios III, VI, VIII y IX)

## Clarifications

### Session 2026-10-22

- Q: ¿Quién puede aprobar el despliegue en el Environment `production`? → A: el otro lead
  (no quien disparó el despliegue), salvo en un hotfix, donde puede aprobar el mismo lead
  dejando por escrito el motivo.
- Q: Si un servicio no queda sano tras el despliegue, ¿qué hace el sistema? → A: vuelve solo
  a la versión anterior de los servicios tocados, marca el despliegue como fallido y avisa a
  ambos leads (FR-007). En el primer despliegue no hay versión anterior: solo avisa.
- Q: ¿Por qué canal llegan las alertas a los leads? → A: correo y un grupo de Telegram de
  los leads (bot), en paralelo (FR-025).
- Q: ¿Cuándo se hace el primer release `develop` → `main`? → A: apenas el pipeline esté
  verde, como release extraordinario aprobado por los dos leads y registrado como excepción
  a ADR-012.
- Q: ¿Se puede desplegar mientras hay un evento en vivo? → A: no. Si hay un evento en curso
  o un stream activo, el despliegue falla antes de tocar el servidor y avisa. Un hotfix puede
  forzarlo con un motivo escrito (FR-028).

## User Scenarios & Testing *(mandatory)*

Actores:

- **Lead:** abre y aprueba el PR de release, aprueba el despliegue en el Environment
  `production`, ejecuta un rollback y recibe las alertas. Hay dos leads.
- **Usuario final:** usa la app móvil y la web contra producción. No interactúa con el
  despliegue, pero sufre sus fallos.

### User Story 1 - Un release aprobado llega a producción sin pasos manuales (Priority: P1)

Un lead mergea el PR de release de `develop` a `main`. El sistema prepara solo las partes del
backend que cambiaron, pide la aprobación de un lead en el Environment `production`, las
instala en el servidor, comprueba que cada servicio quedó sano y deja registrado qué versión
de cada servicio quedó corriendo. Si un servicio no queda sano, el sistema vuelve sola la
versión anterior de los servicios tocados y avisa.

**Why this priority**: sin esto no hay producción. Hoy los pasos de despliegue son `echo`
(O-12) y cada instalación sería manual, lenta y no reproducible.

**Independent Test**: mergear a `main` un release que cambia un solo servicio, aprobar el
despliegue y verificar que solo ese servicio cambió de versión en el servidor, que los demás
siguen corriendo sin reinicio y que la API pública responde.

**Acceptance Scenarios**:

1. **Given** un servidor preparado y un release que cambia solo `event-service`, **When** el
   release entra a `main` y un lead aprueba el despliegue, **Then** solo `event-service` se
   reconstruye e instala, el resto conserva su versión y el despliegue termina en éxito con
   todos los servicios sanos.
2. **Given** un release disparado por un lead, **When** ese mismo lead intenta aprobarlo,
   **Then** la aprobación no cuenta y el despliegue sigue esperando al otro lead.
3. **Given** un hotfix que entra a `main` de noche, **When** el lead que lo mergeó lo aprueba
   escribiendo el motivo, **Then** el despliegue corre y el motivo queda en el registro.
4. **Given** un release en `main` sin aprobación, **When** pasa el tiempo de espera de la
   aprobación, **Then** el servidor no se toca y el despliegue queda como no ejecutado.
5. **Given** un release cuyo `auth-service` no arranca, **When** el despliegue espera la
   salud de los servicios, **Then** a los pocos minutos se restaura la versión anterior de
   los servicios tocados, el despliegue queda en fallo y ambos leads reciben el aviso.
6. **Given** un cambio en un insumo común (la imagen base del backend o el archivo de
   despliegue de producción), **When** entra a `main`, **Then** se reconstruyen todos los
   servicios afectados por ese insumo.
7. **Given** un evento en curso con asistentes haciendo check-in, **When** entra un release
   a `main` y se aprueba, **Then** el despliegue falla sin tocar el servidor y avisa qué
   evento lo bloquea; relanzado después del evento, despliega normal.
8. **Given** un despliegue en curso, **When** entra otro commit a `main`, **Then** el segundo
   espera a que termine el primero; nunca corren dos a la vez.
9. **Given** un servidor nuevo, **When** un lead corre la preparación del servidor dos veces
   seguidas, **Then** el resultado es el mismo y la segunda vez no cambia nada.

---

### User Story 2 - Volver a una versión anterior (Priority: P2)

Un problema aparece horas después de un despliegue exitoso (por ejemplo, un error de
negocio que los healthchecks no detectan). Un lead pide, desde el pipeline, volver a un
release anterior; el sistema lo instala con la misma aprobación y verificación que un
despliegue normal.

**Why this priority**: con un solo servidor y sin staging (ADR-011), el rollback es la única
red de seguridad frente a un release malo.

**Independent Test**: desplegar dos releases seguidos, pedir rollback al primero y
verificar que cada servicio vuelve a la versión que tenía en ese release.

**Acceptance Scenarios**:

1. **Given** los últimos 3 releases desplegados, **When** un lead pide rollback a uno de
   ellos y lo aprueba el otro lead (FR-002), **Then** todos
   los servicios quedan en las versiones de ese release, sin reconstruir nada.
2. **Given** un pedido de rollback a un release que ya no está guardado en el servidor,
   **When** se ejecuta, **Then** el sistema lo rechaza antes de tocar los servicios y explica
   que esa versión no está disponible.

---

### User Story 3 - La web se publica desde `main` en el hosting definitivo (Priority: P2)

Al entrar un release a `main`, la web pasa las mismas puertas de calidad de hoy y se publica
en Cloudflare Pages, apuntando a la API de producción. Los enlaces directos a cualquier
pantalla funcionan. El despliegue a Vercel se retira.

**Why this priority**: el plan gratuito de Vercel prohíbe el uso comercial (ADR-011), y
los usuarios de iPhone dependen de la web mientras no haya app de iOS.

**Independent Test**: mergear a `main` un cambio de frontend, abrir la web publicada en
una ruta interna (enlace directo) y comprobar que carga y habla con la API de producción.

**Acceptance Scenarios**:

1. **Given** un release con cambios de frontend, **When** entra a `main`, **Then** la web
   queda publicada con las URLs de producción de la API y del WebSocket.
2. **Given** que falta la URL de la API o del WebSocket de producción, **When** se construye
   la web, **Then** la publicación falla y la versión anterior sigue en línea (Principio VIII).
3. **Given** un usuario que abre un enlace directo a una pantalla interna, **When** carga la
   página, **Then** ve esa pantalla y no un error 404.

---

### User Story 4 - Los leads se enteran de una caída antes que los usuarios (Priority: P3)

Los servicios y el servidor envían sus métricas, logs y trazas a Grafana Cloud. Hay un tablero
con tráfico, errores y latencia de la API, y alertas por correo y Telegram a ambos leads cuando algo
se cae o el servidor se queda sin recursos.

**Why this priority**: con un solo servidor (punto único de falla, ADR-011) hay que detectar
rápido. Va en P3 porque se puede desplegar sin esto, pero no se debe abrir a usuarios reales
sin esto.

**Independent Test**: detener un servicio en producción y verificar que ambos leads reciben la
alerta dentro del tiempo objetivo; revisar que el tablero muestra datos reales.

**Acceptance Scenarios**:

1. **Given** la API pública caída, **When** pasan 2 minutos, **Then** ambos leads reciben una
   alerta por correo y por Telegram.
2. **Given** el disco o la memoria del servidor por encima del 80 % durante 10 minutos,
   **When** se evalúa la regla, **Then** ambos leads reciben una alerta.
3. **Given** que el backup diario de la base de datos falla o no corre, **When** pasa el
   horario esperado, **Then** ambos leads reciben una alerta.
4. **Given** el tablero de la API, **When** hay tráfico real, **Then** muestra peticiones por
   minuto, porcentaje de errores y latencia p95/p99 por ruta.

---

### User Story 5 - Aprovisionamiento completo y documentado (Priority: P1)

Los leads terminan lo que falta de T-I06 con instrucciones paso a paso: llave SSH del
servidor, Environment `production` con revisores y secretos, dominio y DNS, proyecto de
Pages y el dominio de envío de correo verificado en Resend.

**Why this priority**: bloquea la Historia 1. Son tareas manuales de los leads (ADR-013),
pero sin una lista exacta se olvida algún secreto y el primer despliegue falla.

**Independent Test**: seguir la guía desde cero y comprobar con una verificación automática
que el servidor acepta la llave del pipeline, que todos los secretos requeridos existen y
que el DNS resuelve los subdominios previstos.

**Acceptance Scenarios**:

1. **Given** la guía de aprovisionamiento, **When** un lead la sigue, **Then** una
   verificación previa al despliegue confirma acceso, secretos y DNS sin desplegar nada.
2. **Given** un secreto requerido sin cargar, **When** corre la verificación, **Then** falla
   nombrando el secreto que falta, sin mostrar el valor de ningún otro.

---

### Edge Cases

- **Primer despliegue:** el servidor no tiene versiones previas; no hay a qué volver si
  falla. Debe poder reintentarse y no debe dejar datos a medias en la base.
- **Servidor sin espacio:** guardar versiones viejas llena el disco. Se guardan solo las de
  los últimos 3 releases por servicio y se avisa antes de llenarse.
- **Transferencia cortada:** si se corta la copia de imágenes al servidor, no se instala
  nada y el despliegue falla limpio.
- **Cambio de configuración sin secreto:** un release que necesita una variable nueva que no
  está cargada falla antes de reiniciar servicios, no después.
- **Comprobación de eventos no disponible:** si no se puede saber si hay un evento en curso
  (por ejemplo, la base de datos no responde), el despliegue se trata como bloqueado; un
  hotfix puede forzarlo con motivo.
- **Release de solo documentación:** no construye ni reinicia nada.
- **Huella del servidor cambiada:** si la identidad SSH del servidor no coincide con la
  registrada, el pipeline se niega a conectar (posible suplantación).
- **Servicios con estado** (base de datos, caché, mensajería): un despliegue de aplicación
  no los reinicia salvo que cambie su propia definición.
- **PR desde un fork:** nunca ve secretos ni puede disparar un despliegue (repo público).

## Requirements *(mandatory)*

### Functional Requirements

**Despliegue del backend (T-I08)**

- **FR-001**: El despliegue DEBE dispararse solo cuando un commit entra a `main` (release o
  hotfix, ADR-012) o cuando un lead pide un rollback; nunca desde otras ramas ni PRs.
- **FR-002**: Antes de tocar el servidor, el despliegue DEBE esperar la aprobación de un lead
  en el Environment `production` (constitución v1.3.0). Sin aprobación en 24 h, se cancela.
  - Release y rollback: el aprobador DEBE ser un lead distinto de quien disparó el despliegue.
  - Hotfix (commit que entra a `main` desde `hotfix/*`, ADR-012): PUEDE aprobarlo el mismo lead,
    y el despliegue DEBE registrar el motivo escrito de la excepción.
- **FR-003**: El sistema DEBE construir solo los servicios cuyo código cambió desde el último
  despliegue exitoso, más todos los afectados cuando cambia un insumo común.
- **FR-004**: Cada servicio DEBE poder tener su propia versión en producción; un servicio sin
  cambios sigue con la versión que ya corre, sin reconstruirse ni reiniciarse.
- **FR-005**: La versión de cada servicio DEBE ser el SHA del commit que la construyó; nunca
  `latest` (Principio VIII).
- **FR-006**: Las imágenes DEBEN llegar al servidor sin pasar por un registro público.
- **FR-007**: Tras instalar, el sistema DEBE verificar la salud de cada servicio tocado en
  un máximo de 5 minutos; si alguno no queda sano, DEBE restaurar la versión anterior de los
  servicios tocados, marcar el despliegue como fallido y avisar a ambos leads, sin esperar
  intervención manual.
- **FR-008**: Un lead DEBE poder pedir rollback a cualquiera de los últimos 3 releases
  desplegados, con la misma aprobación y verificación de salud, sin reconstruir imágenes.
- **FR-009**: Nunca DEBEN correr dos despliegues a la vez; el segundo espera.
- **FR-010**: Cada despliegue DEBE dejar registro de: commit, versión de cada servicio,
  quién aprobó, resultado y duración. El registro se consulta en el historial del pipeline
  y en el servidor.
- **FR-011**: Los servicios con estado (base de datos, caché, mensajería, borde) NO DEBEN
  reiniciarse salvo que cambie su definición.
- **FR-012**: El despliegue DEBE validar la configuración completa contra los secretos del
  servidor antes de reiniciar nada, y fallar nombrando lo que falta.
- **FR-028**: Antes de tocar el servidor, el despliegue (incluido el rollback) DEBE
  comprobar si hay un evento en curso (entre su inicio y su fin) o un stream activo. Si lo
  hay, DEBE fallar sin cambiar nada, avisando a los leads cuál evento lo bloquea, y puede
  relanzarse después. Solo un hotfix PUEDE forzarlo, con un motivo escrito que queda en el
  registro (FR-010). Un rollback forzado sigue la misma regla.

**Servidor y secretos (T-I06)**

- **FR-013**: La preparación del servidor DEBE ser repetible e idempotente: usuario sin
  privilegios de root para desplegar, acceso solo por llave, firewall con 22 (restringido),
  80, 443, 1935 y los puertos UDP de WebRTC, y el directorio de despliegue.
- **FR-014**: El pipeline DEBE verificar la identidad del servidor contra una huella
  registrada antes de conectar.
- **FR-015**: Los secretos DEBEN vivir solo en el Environment `production`, en el gestor de
  contraseñas y en el archivo de entorno del servidor con permisos de solo dueño; nunca en
  el repo, en las imágenes ni en los logs del pipeline (Principio III; el repo es público).
- **FR-016**: DEBE existir una guía de aprovisionamiento con la lista exacta de secretos,
  registros DNS, cuentas y el bot y grupo de Telegram de alertas, y una verificación que la compruebe sin desplegar.
- **FR-017**: El dominio de envío de correo DEBE quedar verificado en Resend (registros de
  autenticación de correo en el DNS) y su llave guardada como secreto. Enviar correos desde
  la app queda fuera de esta feature.

**Web (T-I11)**

- **FR-018**: La web DEBE publicarse en Cloudflare Pages cada vez que entra a `main` un
  cambio de frontend, con las mismas puertas de calidad que hoy (análisis, tests).
- **FR-019**: La publicación de la web DEBE fallar si faltan las URLs de producción de la
  API o del WebSocket (T-F06, Principio VIII).
- **FR-020**: Los enlaces directos a rutas internas DEBEN cargar la app (comportamiento que
  hoy da `vercel.json`).
- **FR-021**: La publicación de la web DEBE requerir la misma aprobación del Environment
  `production` que el backend.
- **FR-022**: El flujo de despliegue a Vercel DEBE retirarse una vez verificada la primera
  publicación en Pages.

**Observabilidad (T-I13)**

- **FR-023**: El servidor DEBE enviar a Grafana Cloud las métricas de los servicios y del
  host, los logs de todos los contenedores y las trazas de las peticiones (Principio VI).
- **FR-024**: DEBE existir un tablero de la API con peticiones por minuto, porcentaje de
  errores y latencia p95/p99 por ruta.
- **FR-025**: DEBEN existir alertas a ambos leads, por correo y por un grupo de Telegram a la vez, para: API pública caída más de
  2 minutos (monitor externo), un servicio no sano más de 5 minutos, disco o memoria > 80 %
  durante 10 minutos, y backup diario fallido o ausente.
- **FR-026**: Los logs enviados NO DEBEN contener tokens, contraseñas ni datos personales
  sin enmascarar (Principios III y IX).
- **FR-027**: El uso de Grafana Cloud DEBE caber en el plan gratuito (ADR-011); el volumen
  enviado se limita o se filtra para lograrlo.

### Key Entities

- **Release:** commit que entra a `main`. Atributos: SHA, PR de origen, fecha, lista de
  servicios cambiados.
- **Versión de servicio:** par (servicio, SHA) instalado en el servidor. Un release fija
  una versión por cada servicio; las no cambiadas se heredan del release anterior.
- **Despliegue:** intento de llevar un release, un hotfix o un rollback al servidor.
  Atributos: tipo, release, versiones resultantes, aprobador, motivo (solo en hotfix con
  autoaprobación), resultado, duración.
- **Manifiesto de versiones:** la lista vigente de versiones por servicio en el servidor;
  se guardan los últimos 3 para poder volver.
- **Alerta:** regla, umbral, duración, destinatarios (ambos leads).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde la aprobación del despliegue hasta tener producción actualizada y sana
  pasan como máximo 15 minutos cuando cambia un solo servicio.
- **SC-002**: Un release que cambia un servicio no reinicia ninguno de los demás.
- **SC-003**: Un rollback a un release anterior deja producción sana en 5 minutos o menos
  desde su aprobación.
- **SC-004**: Un despliegue fallido deja producción en la versión anterior y sana, sin
  intervención manual, en 10 minutos o menos.
- **SC-005**: Ambos leads reciben la alerta de caída de la API en 5 minutos o menos desde que
  empieza la caída.
- **SC-006**: El 100 % de los despliegues quedan con aprobador y versiones registradas.
- **SC-007**: Ningún secreto aparece en el repo, en los logs del pipeline ni en las imágenes
  (verificado con un escaneo de secretos en CI).
- **SC-008**: El costo mensual de la infraestructura sigue dentro del presupuesto de ADR-011
  (100.000 COP); esta feature no agrega servicios pagos.

## Assumptions

- El servidor es el VPS Contabo de 12 GB en EE. UU. de ADR-011, con un sistema Linux
  compatible con Docker. La preparación del servidor la ejecuta un lead (ADR-013); el
  agente no recibe acceso SSH ni ve valores de secretos.
- El dominio se registra (o ya está) en Cloudflare con DNS en Cloudflare. Subdominios
  previstos: `api.` (API y WebSocket), `live.` (streaming), `media.` (archivos de R2) y uno
  para la web (por defecto `app.`). El nombre del dominio se carga como configuración, no
  se escribe en el repo.
- Se acepta una interrupción breve (menos de 1 minuto) de cada servicio que se reinicia
  fuera de eventos en vivo (FR-028); despliegue sin cortes queda para la Fase Crecimiento.
- El primer release `develop` → `main` (hoy 78 commits de diferencia) se hace apenas el
  pipeline esté verde, como release extraordinario aprobado por los dos leads, y no espera
  al viernes. La excepción se registra en la sección *Actualizaciones* de ADR-012. Desde ahí
  rige la cadencia semanal.
- La base de datos de producción arranca vacía; no hay migración de datos.
- El monitor externo de disponibilidad es el de Grafana Cloud (gratuito).
- Enviar correos desde la app (verificación, invitaciones) es otra feature; aquí solo se deja
  Resend listo para usar.
- Las apps móviles y su publicación en tiendas quedan fuera (D-10 pospuesta).
