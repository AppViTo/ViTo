# CONTRIBUTING — ViTo

Guía de colaboración para el **equipo frontend** y el **equipo backend** que trabajan sobre este mismo monorepo, cada uno en sus propias ramas.

> **Regla de oro:** nadie implementa un endpoint que no esté en `docs/api/openapi.yaml`.
> Si necesitas algo que no existe en el contrato, abre un RFC (issue `contract-change`), haz merge del contrato, y luego implementa.

> **¿Qué hago ahora?** El trabajo se toma de [`docs/roadmap/tasks.md`](docs/roadmap/tasks.md)
> (solo tareas 🟢 `READY`). Agentes de IA: leer primero
> [`docs/handoff/AGENT-BRIEF.md`](docs/handoff/AGENT-BRIEF.md).

---

## 1. Estructura del repositorio y propiedad de áreas

| Área | Ruta | Dueño | Quién aprueba |
|------|------|-------|---------------|
| Contrato de API | `docs/api/**` | Frontend (lead) | **Al menos 1 lead** (CODEOWNER) |
| Decisiones de contrato | `docs/decisions/**` | Frontend (lead) | **Al menos 1 lead** (CODEOWNER) |
| Handoffs a backend | `docs/handoff/**` | Frontend | Frontend lead (o backend lead si el autor es el frontend lead) |
| Hoja de ruta | `docs/roadmap/**` | Ambos | Al menos 1 lead (CODEOWNER) |
| Frontend Flutter | `frontend/**` | Frontend | Frontend lead (o backend lead si el autor es el frontend lead) |
| Backend Java/Python | `backend/**`, `ai-services/**` | Backend | Backend lead (o frontend lead si el autor es el backend lead) |
| Infra y CI | `infrastructure/**`, `.github/workflows/**` | DevOps | Al menos 1 lead (CODEOWNER) |
| Docs de arquitectura | `docs/architecture.md`, `AGENTS.md` | Ambos | Al menos 1 lead (CODEOWNER) |

Esto está codificado en `.github/CODEOWNERS`. Cuando una ruta tiene varios dueños, **basta la aprobación de uno de ellos** (semántica nativa de GitHub). Sin al menos una aprobación de un dueño, el PR no se mergea.

**Revisión cruzada:** ambos leads figuran como dueños de todas las áreas de código, porque GitHub no cuenta la aprobación del autor y un área con un solo dueño dejaría sin revisor posible los PR de ese mismo lead. El lead del área sigue siendo el revisor por defecto. La aprobación se registra con **"Approve" en GitHub** antes del merge; el merge por sí solo no cuenta como aprobación.

> Nota: la protección de rama no está disponible en el plan actual de la organización (repo privado en plan Free), así que esta regla hoy se cumple por acuerdo del equipo, no por GitHub. Al pasar a GitHub Team se activará en `main` y `develop`: aprobación requerida de CODEOWNERS, mínimo 1 aprobación, sin push directo y CI en verde.

---

## 2. Ramas

```
main          ← estable. Prohibido hacer push directo. Solo merge de PR.
develop       ← integración. Los PRs de feature apuntan aquí.
│
├─ contract/<nombre>     cambios en docs/api/** o docs/decisions/**
├─ frontend/<nombre>     cambios en frontend/**
├─ backend/<nombre>      cambios en backend/** y ai-services/**
└─ docs/<nombre>         cambios en documentación no contractual
```

Convenciones:

- Nombre corto y descriptivo: `frontend/fix-auth-token-persistence`, `backend/add-mosaic-ws`.
- Rama vive **7 días o menos**; si se alarga, rebasea contra `develop` y avisa en el canal.
- Un feature = una rama. Un PR = un feature.
- **PR dependientes:** todo PR apunta a `develop`, nunca a la rama de otro PR. Si un PR depende de otro, se abre igualmente contra `develop` y se indica "mergear después de #N" en la descripción. Un PR apilado (con base en otra rama de feature) que se mergea después de su base deja su código fuera de `develop` (pasó con #20, re-entregado en #22).

### Flujo

```
0. Tomar una tarea READY         docs/roadmap/tasks.md → marcarla 🔵 EN CURSO (<rama>)
1. Crear rama desde develop:     git checkout develop && git pull && git checkout -b frontend/<id>-<nombre>
2. Trabajar y commitear          convención de commits abajo
3. Push y abrir PR → develop     checklist del PR template + "Closes T-xx"
4. Aprobación del CODEOWNERS     al menos 1 dueño de las rutas tocadas (el autor no cuenta)
5. Merge (squash)                tarea ✅ HECHA (#PR) en tasks.md; borrar la rama
```

### Release y hotfix (ADR-012)

```
Release semanal (viernes)    PR "release: AAAA-MM-DD"  develop → main
                             CI verde + Approve de 1 CODEOWNER distinto del autor
                             merge commit (no squash) → tag v0.MINOR.PATCH → despliegue (ADR-011)
Hotfix                       hotfix/<nombre> desde main → PR a main → PR main → develop
```

La aprobación del PR de release es la aprobación manual de producción. Nadie hace push
directo a `main` ni a `develop`.

---

## 3. Commits

Formato [Conventional Commits](https://www.conventionalcommits.org/):

```
<tipo>(<ámbito>): <descripción en imperativo>

tipo: feat | fix | docs | refactor | test | chore | ci | contract
ámbito: auth | events | checkin | social | mosaic | streaming | chat | archive | media | gateway | api | app
```

Ejemplos:

```
contract(api): add checkin endpoints and schemas
feat(events): wire event list page to EventBloc
fix(auth): persist access and refresh tokens on login
docs(handoff): add events handoff for backend team
```

---

## 4. Cambios al contrato (el proceso central)

Cualquier cambio a `docs/api/openapi.yaml` o `docs/api/asyncapi.yaml` sigue **siempre** este camino:

```
1. Abrir issue con el template "Change de contrato (RFC)"
2. Discutir en el issue (asignar al dueño relevante)
3. Si se aprueba: rama contract/<nombre> con el diff del contrato
   - actualizar openapi.yaml / asyncapi.yaml
   - actualizar o crear los fixtures en docs/api/fixtures/
   - registrar la decisión en docs/decisions/adr-NNN.md
4. PR a develop → requiere aprobación de al menos 1 lead (CODEOWNER), distinto del autor
5. Los equipos actualizan su código en el mismo sprint
```

**Nunca** se implementa primero y se documenta después. El contrato es previo a la implementación.

Si el backend necesita variar un detalle por una limitación técnica, se discute en el RFC y se resuelve *en el contrato* — no en el código.

---

## 5. Definición de Done por feature

La feature está terminada solo cuando **las tres** columnas están en verde:

| Contrato | Frontend | Backend |
|----------|----------|---------|
| [ ] Endpoint en `openapi.yaml` (o `asyncapi.yaml` si es WS) | [ ] Pantalla conectada de punta a punta | [ ] Endpoint implementado |
| [ ] Fixture en `docs/api/fixtures/` | [ ] Test de serialización verde | [ ] `schemathesis` verde |
| [ ] Handoff en `docs/handoff/<feature>.md` | [ ] `flutter analyze` + `flutter test` verdes | [ ] `mvn test` verde |
| [ ] Mock sirve la feature end-to-end | [ ] Sin TODOs ni mocks en el flujo | [ ] Sin divergencias del contrato |

---

## 6. Comandos

```bash
# Frontend
cd frontend/vito_app
flutter pub get
flutter analyze --fatal-infos
flutter test

# Contrato (lint)
npx @stoplight/spectral-cli lint docs/api/openapi.yaml
npx @asyncapi/cli validate docs/api/asyncapi.yaml

# Mocks locales  (disponibles al cerrar la fase "Mocks" del plan — Sprint 2)
npm run mock:api      # REST en http://localhost:4010  (Prism)
npm run mock:ws       # WS  en ws://localhost:4011     (script propio)

# Flutter contra el mock (sin backend real)
cd frontend/vito_app && flutter run \
  --dart-define=API_BASE_URL=http://localhost:4010/api/v1 \
  --dart-define=WS_BASE_URL=ws://localhost:4011

# Backend (cada servicio es independiente, no hay wrapper Maven)
cd backend/event-service && mvn clean test
```

---

## 7. Comunicación y cadencia

| Cuándo | Qué |
|--------|-----|
| **Lunes, 30 min** | Kickoff de la feature de la semana. Si toca contrato → RFC ese mismo día. |
| **Miércoles** | Sync de blockers (issues con label `blocked-by-backend` / `blocked-by-frontend`). |
| **Viernes** | Review de integración: mocks actualizados, fixtures verificados, PRs de contrato mergeados, revisión de `docs/roadmap/tasks.md` y `decisions-pending.md`. |

Labels disponibles: `contract`, `feat:auth`, `feat:events`, `feat:checkin`, `feat:social`, `feat:mosaic`, `feat:streaming`, `feat:chat`, `feat:archive`, `feat:media`, `blocked-by-backend`, `blocked-by-frontend`, `bug:contract`.
