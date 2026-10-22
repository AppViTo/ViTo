# ADR-011: Stack de despliegue de la Fase Tracción (presupuesto 100.000 COP/mes)

- **Estado:** Aceptada
- **Fecha:** 2026-10-17
- **Decisor:** Ambos leads (acordado en la sesión de decisiones del 2026-10-17; se formaliza con la aprobación del PR)
- **Resuelve:** D-02 (sub-decisiones a–k) y D-04 de `docs/roadmap/decisions-pending.md`
- **Constitución:** habilita el perfil *Fase Tracción* de la sección "Perfiles de Despliegue" (v1.2.0)

## Contexto

La constitución v1.0 describía un despliegue de producción "de manual" (Kubernetes + ArgoCD,
Kafka con replicación 3, MongoDB gestionado con replica set, Prometheus/Grafana propios,
gestor de secretos). La v1.1.0 congeló esas reglas por costo.

Restricción real: **100.000 COP al mes** (≈ 31 USD con la TRM de 3.170 COP/USD del
2026-10-16). Los servicios cobrados desde el exterior llevan **IVA del 19 %** más el margen
cambiario del banco: cada USD de lista cuesta ~20–22 % más.

Objetivo: una app funcional que soporte eventos reales, genere los primeros ingresos y
financie con ellos el crecimiento de la infraestructura.

Estimación de memoria del stack: 10 servicios Spring Boot con heap limitado (~4 GB),
Kafka con un solo broker (~1 GB), MongoDB (~1 GB), transcode + moderation + Redis + SRS
(~1–1,5 GB). Total ~7–8 GB: se necesita una máquina de **12 GB**.

## Decisión

### Fase Tracción (vigente desde este ADR)

| # | Tema | Decisión |
|---|------|----------|
| a | Orquestación | **1 VPS con Docker Compose** (`docker-compose.prod.yml`). Sin Kubernetes ni ArgoCD. |
| b | Mensajería (D-04) | **Kafka KRaft, 1 broker** en el VPS, factor de replicación 1, tópicos declarados. Se mantiene Kafka (no Redis Streams) para no reescribir productores. |
| c | MongoDB | **Autoalojado en el VPS**, con autenticación, sin puerto público. `mongodump` diario a R2 con retención de 14 días y una restauración de prueba al mes. |
| d | Servicios | Se despliegan los 10 servicios Java con límites de memoria (`-XX:MaxRAMPercentage`, `mem_limit`), más transcode y moderation. **recommendation-service no se despliega** (diferido en el inventario). |
| e | Almacenamiento de objetos | **Cloudflare R2** en producción (10 GB gratis, egress gratis). SeaweedFS solo para desarrollo local. Ambos con la API S3: el endpoint se configura por entorno. |
| f | Caché / pub-sub | **Redis 7** (o Valkey). Dragonfly descartado para esta fase. |
| g | Descubrimiento | **DNS de Docker Compose**. Eureka descartado. |
| h | Observabilidad | **Grafana Cloud (plan gratuito)** para métricas, logs y trazas OTel, más un monitor de uptime gratuito. Health checks, métricas Prometheus y logs JSON siguen obligatorios. |
| i | Secretos | **Secretos de GitHub Environments** y un `.env` en el VPS (`chmod 600`, fuera del repo). Los servicios fallan al arrancar si falta un secreto. |
| j | Imágenes | **Sin registro.** CI construye la imagen de cada servicio cambiado (tag = SHA) y la copia al VPS con `docker save \| ssh docker load`. Prohibido `:latest`. |
| k | Streaming | **SRS autoalojado** en el VPS, sin transcodificar. Ant Media descartado. |
| — | Proveedor de cómputo | **Contabo Cloud VPS, 12 GB / 6 vCPU, región EE. UU.** (~60–90 ms desde Colombia, tráfico con política de uso justo). |
| — | Frontend web | **Cloudflare Pages** (el plan Hobby de Vercel prohíbe el uso comercial). |
| — | TLS y borde | **Caddy** con Let's Encrypt delante del gateway; solo los puertos 80/443 (y RTMP/WebRTC de SRS) quedan públicos. |
| — | Dominio y DNS | Dominio `.com` en Cloudflare Registrar; DNS en Cloudflare. |
| — | Correo y push | Plan gratuito de Brevo o Resend; FCM para push. |
| — | Pagos | Pasarela local sin mensualidad (Wompi o Mercado Pago) con checkout alojado: la app nunca toca datos de tarjeta. Qué se cobra se decide en D-11. |
| — | Tiendas | **Google Play** (25 USD, pago único; tipo de cuenta en D-10). **iOS diferido**: los usuarios de iPhone usan la web hasta abrir la cuenta de Apple Developer. |
| — | Staging | No hay ambiente de staging permanente. Se valida con CI y `docker compose` local. Si hay capacidad, staging opcional en Oracle Always Free. |
| — | Promoción a producción | El despliegue sale de `main`. La aprobación manual es la aprobación del PR que entra a `main` (ver D-07). GitHub Free no permite revisores obligatorios en Environments de repos privados. |

### Presupuesto resultante (precios verificados el 2026-10-17, con IVA)

| Concepto | COP al mes |
|----------|-----------:|
| VPS Contabo 12 GB (EE. UU.) | ~47.000 |
| Dominio `.com` (prorrateado) | ~3.500 |
| R2, Pages, Grafana Cloud, correo, FCM, TLS | 0 (R2 ~2.000 por cada 40 GB extra) |
| **Total recurrente** | **≈ 50.000–55.000** |
| **Reserva** (provisión para Apple Developer y excedentes) | **≈ 45.000** |

Pagos únicos: Google Play ≈ 95.000 COP (en el mes 1 o 2). Apple Developer
(≈ 370.000 COP/año) se activa con el disparador correspondiente.

### Perfil de carga de la Fase Tracción

Eventos de hasta **~150 asistentes** y **~100 espectadores por stream**. Un stream en 720p
consume ~0,7 GB por espectador-hora (100 espectadores × 2 h ≈ 135 GB). Por encima de eso
la conexión de un VPS de este precio no alcanza. Los SLO de latencia del Principio VII se
mantienen; cambia la carga con la que se miden.

### Disparadores para pasar a Fase Crecimiento

Regla general: **se sube de nivel cuando los ingresos del mes cubren dos veces el costo
del siguiente nivel**, o cuando ocurre un disparador técnico:

| Disparador | Paso | Costo aprox. (COP/mes) |
|------------|------|-----------------------:|
| Usuarios de iPhone piden la app | Apple Developer | +31.000 |
| Mongo > ~5 GB, o perder 24 h de datos deja de ser aceptable | MongoDB Atlas Flex (8–30 USD) | +30.000 a 115.000 |
| RAM del VPS > 80 % sostenido, o se necesita staging | Segundo VPS (staging o división de carga) | +47.000 |
| Eventos > 150 asistentes o streams > 100 espectadores | VPS dedicado para SRS | +47.000 |
| Varios nodos y necesidad de alta disponibilidad | k3s/Kubernetes + ArgoCD, Kafka replicado, Mongo replica set | según diseño |

Cada paso se registra como actualización de este ADR (sección *Actualizaciones*).

## Consecuencias

- Las reglas congeladas en la v1.1.0 se reemplazan por el perfil *Fase Tracción* de la
  constitución v1.2.0. Las reglas originales pasan al perfil *Fase Crecimiento* y vuelven a
  ser obligatorias al cumplir los disparadores.
- Un solo VPS es un punto único de falla. Se mitiga con backups diarios fuera del VPS (R2),
  despliegue reproducible con Compose y un runbook de restauración (`docs/runbooks/`).
- Dependencia de Contabo (CPU compartida, soporte lento). Se mitiga con portabilidad: todo
  el despliegue cabe en `docker-compose.prod.yml` + `.env` + backup de R2.
- CI: el plan gratuito de GitHub Actions da 2.000 minutos al mes en repos privados. Hay que
  construir y probar solo los servicios cambiados (filtros `paths`).
- Datos personales en EE. UU.: transferencia internacional según la Ley 1581 de 2012. Hay
  que verificarlo y declararlo en la política de privacidad (ver D-12).

## Alternativas consideradas

| Alternativa | Por qué no |
|-------------|------------|
| Hetzner CAX31 (16 GB ARM, ~21,5 €) | ≈ 93.000 COP con IVA: consume todo el presupuesto. Stock limitado desde septiembre de 2026. En EE. UU. solo incluye 1 TB de tráfico. |
| Hetzner CAX21 (8 GB) | Memoria insuficiente para el stack. |
| Oracle Always Free (desde junio de 2026: 2 OCPU / 12 GB) | 0 COP, pero falta de capacidad, reclamo de instancias ociosas y sin garantías. Aceptable solo para staging. |
| MongoDB Atlas M0 | 512 MB, sin backups, ~100 ops/s. Insuficiente para producción. |
| Vercel Hobby | Prohíbe el uso comercial; Pro cuesta 20 USD/mes. |
| Kubernetes gestionado | El plano de control y los nodos cuestan varias veces el presupuesto. |

## Actualizaciones

### 2026-10-22 — Repo público y cuentas aprovisionadas

- **El repo es público desde el 2026-10-19** (ver ADR-012). Esto invalida dos supuestos
  de este ADR:
  - *Promoción a producción:* en repos públicos, GitHub Free **sí** permite revisores
    obligatorios en Environments. Producción pasa a requerir dos aprobaciones: el PR de
    release a `main` (ADR-012) y la aprobación de un lead en el Environment `production`
    (constitución v1.3.0, Principio VIII).
  - *Minutos de CI:* los runners estándar de GitHub Actions no tienen límite de minutos en
    repos públicos. T-I12 (CI incremental) se mantiene por tiempo de feedback, no por cuota.
- **Consecuencia de seguridad:** todo lo que entra al repo es público. IP del VPS, correos
  de las cuentas, IDs de cuenta de Cloudflare y valores del `.env` viven solo en GitHub
  Environments, en el gestor de contraseñas y en el VPS.
- **Aprovisionado (T-I06):** VPS Contabo 12 GB en EE. UU., buckets R2 en Cloudflare, cuenta
  de Grafana Cloud y correo de equipo (Gmail, ADR-013).
- **Pendiente (T-I06):** llave SSH del VPS, Environment `production` con sus secretos y
  configuración de **Resend** como proveedor de correo transaccional (se descarta Brevo).

### 2026-10-22 — Frontend web: por qué Cloudflare Pages y criterios de salida

- **Por qué no GitHub Pages:** no permite cabeceras HTTP propias, así que no cumple
  T-I18 (CSP, HSTS, `X-Frame-Options`), y sus términos no lo admiten como hosting de un
  producto comercial.
- **Por qué Cloudflare Pages:** uso comercial permitido en el plan gratuito, tráfico
  estático sin límite, archivo `_headers` para T-I18 y `_redirects` para las rutas de la
  SPA, y el mismo proveedor que el DNS y R2. La web se compila en GitHub Actions y se
  sube con *direct upload* (`wrangler`), así que no consume los builds de Cloudflare.
- **Riesgo aceptado:** DNS, R2, backups y web quedan en una sola cuenta de Cloudflare.
  Mitigación: 2FA en las cuentas de los dos leads y procedimiento de recuperación en el
  gestor de contraseñas.
- **Criterios de salida:**

  | Disparador | Paso |
  |------------|------|
  | Cloudflare anuncia el fin de Pages o la web necesita lógica en el *edge* | Migrar a Cloudflare Workers con *static assets* (mismo `_headers`) |
  | Caída prolongada de Cloudflare o pérdida de la cuenta | Plan de emergencia: servir `build/web` desde Caddy en el VPS |
  | Salir de Cloudflare por otra razón | Firebase Hosting o Netlify (ambos admiten cabeceras y uso comercial) |
  | La web pasa a necesitar SSR | Reevaluar Vercel Pro en un ADR nuevo |

- La web es una carpeta de archivos estáticos: cambiar de proveedor solo toca el job de
  `deploy-frontend.yml` y un registro DNS.
