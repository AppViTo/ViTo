#!/usr/bin/env node
// Valida docs/api/asyncapi.yaml con @asyncapi/parser (CI: "Validate asyncapi.yaml").
// Equivale a `asyncapi validate` de @asyncapi/cli, sin su árbol de generadores.
//
// Uso: node scripts/validate-asyncapi.mjs   (o `npm run lint:async`)

import { readFileSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Parser } from '@asyncapi/parser';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const file = join(root, 'docs/api/asyncapi.yaml');

// Severidades LSP: 0 error, 1 warning, 2 information, 3 hint.
const LABEL = ['error', 'warning', 'information', 'hint'];

const parser = new Parser();
const { diagnostics } = await parser.parse(readFileSync(file, 'utf8'), { source: file });

for (const d of diagnostics) {
  const line = d.range.start.line + 1;
  console.log(`${relative(root, file)}:${line} ${LABEL[d.severity]} ${d.code}: ${d.message}`);
}

const errors = diagnostics.filter((d) => d.severity === 0).length;
if (errors > 0) {
  console.error(`✖ asyncapi.yaml: ${errors} error(es)`);
  process.exit(1);
}
console.log(`✔ asyncapi.yaml válido (${diagnostics.length} aviso(s) informativos)`);
