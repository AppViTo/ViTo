---
name: Bloqueo entre pistas
about: Una tarea de tu pista depende de una tarea de la otra pista (T-I15)
title: "[blocked] T-xx ← T-yy: "
labels: ''
---

<!--
FLUJO (AGENT-BRIEF §9 y CONTRIBUTING §7)
1. Etiqueta el issue con la pista que te bloquea:
   - blocked-by-backend  → la tarea que necesitas es T-B o T-I (pista backend/infra).
   - blocked-by-frontend → la tarea que necesitas es T-C o T-F (pista contrato/frontend).
2. NO implementes la tarea de la otra pista, ni un parche de ella en tu código.
3. Marca tu tarea 🔴 BLOQUEADA por T-yy en docs/roadmap/tasks.md y enlaza este issue.
4. La otra pista atiende estos issues antes que sus tareas P1/P2 (AGENT-BRIEF §7).
5. La prioridad de T-yy la cambian los leads en el sync del miércoles, no el agente.
Ciérralo cuando T-yy esté ✅ HECHA y tu tarea vuelva a 🟢 READY.
-->

## 1. Tarea bloqueada (tu pista)

- **Tarea:** T-xx — <título>
- **Pista:** backend/infra (T-B, T-I) · contrato/frontend (T-C, T-F)
- **Qué no se puede hacer sin la otra tarea:**

## 2. Tarea que se propone adelantar (la otra pista)

- **Tarea:** T-yy — <título> (estado actual en `tasks.md`: )
- **Qué parte basta para desbloquear:** <!-- toda, o solo un punto concreto -->

## 3. Impacto

<!-- Qué se retrasa y cuánto: hito del MVP, ola, release, bug en producción. -->

## 4. Rodeo temporal

<!-- ¿Se puede avanzar mientras tanto? P. ej. Prism (`npm run mock:api`) con los
     fixtures del contrato, un feature flag, o "ninguno". Un rodeo nunca implementa
     la tarea de la otra pista. -->

## 5. Al cerrar

- [ ] T-yy ✅ HECHA (#PR)
- [ ] T-xx vuelve a 🟢 READY en `tasks.md`
