# Documentación en PDF

Documentos curados para personas, generados a partir del repositorio. El repo es la fuente
de verdad; estos PDF son una vista. Los mantiene el agente `doc-pdf`
(`.claude/agents/doc-pdf.md`).

- `src/` — fuente Markdown de cada documento (se versiona).
- `out/` — PDF y HTML compilados (no se versiona; se regenera).

Compilar:

```bash
npm install --prefix tools/docs-pdf
node tools/docs-pdf/build.mjs docs/pdf/src/<doc>.md
```

## Catálogo

| Documento | Audiencia | Fuentes principales | Versión | Fecha |
|-----------|-----------|---------------------|---------|-------|
| arquitectura | Técnica | ADR-001 a 018, CONVENTIONS, constitución (Perfiles de Despliegue), AGENT-BRIEF, tasks.md, docker-compose*.yml, Caddyfile | 1.3 (Borrador) | 2026-10-30 |
