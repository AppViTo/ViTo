# ADR-013: Titularidad de cuentas, credenciales y medio de pago

- **Estado:** Aceptada
- **Fecha:** 2026-10-17
- **Decisor:** Ambos leads (acordado el 2026-10-17; se formaliza con la aprobación del PR)
- **Resuelve:** D-09 de `docs/roadmap/decisions-pending.md`
- **Relacionado:** ADR-011 (cuentas que hay que abrir), T-I06 (aprovisionamiento)

## Contexto

ADR-011 requiere abrir cuentas en varios proveedores y pagarlas con tarjeta en moneda
extranjera. Si quedan en correos personales o con una sola persona que conoce las
credenciales, el proyecto depende de esa persona y no hay forma limpia de traspasar o
revocar accesos.

## Decisión

1. **Correo de equipo como dueño de todas las cuentas.** Mientras no exista el dominio,
   una cuenta de correo gratuita dedicada al proyecto (p. ej. `vito.infra@…`). Cuando
   exista el dominio, `infra@<dominio>` con Cloudflare Email Routing (gratuito) que
   reenvía a ambos leads, y se cambia el correo de contacto de cada cuenta.
2. **Ambos leads como administradores** en cada proveedor que lo permita (GitHub,
   Cloudflare, Grafana Cloud, Google Play Console). Donde solo hay un usuario (Contabo),
   la credencial vive en el gestor compartido.
3. **Gestor de contraseñas compartido** (p. ej. organización gratuita de Bitwarden para
   2 usuarios), con **2FA obligatorio** en todas las cuentas. Los códigos de recuperación
   de 2FA se guardan **fuera** del gestor, en una copia que custodian ambos leads.
4. **Una sola tarjeta de pago** (virtual si el banco la ofrece, o la de la empresa cuando
   exista), para que los cobros se puedan rastrear y bloquear en un solo lugar.
5. **Registro de gastos** en una hoja compartida: proveedor, fecha, monto en la moneda
   original, monto en COP con IVA y quién pagó. Se revisa en la review de integración del
   último viernes de cada mes contra el presupuesto de ADR-011. Los leads acuerdan por
   escrito cómo se reparten los costos mientras no haya ingresos.
6. **Alertas a ambos leads**: avisos de cobro y de límite de gasto de cada proveedor,
   alertas de caída (T-I13) y vencimiento del dominio.
7. **Revocación**: si un lead deja el proyecto, se rotan todas las credenciales del
   gestor, se le quita el acceso de administrador y se regeneran los secretos de GitHub
   Environments y del `.env` del VPS.

### Inventario de cuentas (se completa en T-I06)

| Proveedor | Para qué (ADR-011) | Dueño | Administradores | Cobro |
|-----------|--------------------|-------|-----------------|-------|
| GitHub (org AppViTo) | Código, CI, secretos | org | ambos leads | gratis |
| Contabo | VPS | correo de equipo | gestor compartido | mensual |
| Cloudflare | Dominio, DNS, R2, Pages, Email Routing | correo de equipo | ambos leads | anual (dominio) |
| Grafana Cloud | Observabilidad | correo de equipo | ambos leads | gratis |
| Brevo o Resend | Correo transaccional | correo de equipo | gestor compartido | gratis |
| Firebase (FCM) | Push | correo de equipo | ambos leads | gratis |
| Google Play Console | Publicación Android | según D-10 | ambos leads | pago único |

## Consecuencias

- T-I06 (aprovisionar cuentas y VPS) queda desbloqueada.
- Nadie usa su correo o tarjeta personal sin registrarlo en la hoja de gastos.
- Ninguna credencial ni código de recuperación se guarda en el repositorio (Principio III).

## Actualizaciones

*(ninguna todavía)*
