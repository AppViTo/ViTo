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

// --- Fixtures de frames (T-C05) --------------------------------------------
// Cada mensaje de components.messages tiene docs/api/fixtures/ws/<messageId>.json
// y valida contra su payload, con los objetos cerrados como en
// validate-fixtures.mjs. No puede haber fixtures huérfanos.
import { readdirSync, existsSync } from 'node:fs';
import { parse as parseYaml } from 'yaml';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const doc = parseYaml(readFileSync(file, 'utf8'));
const wsDir = join(root, 'docs/api/fixtures/ws');

function closed(node) {
  if (Array.isArray(node)) return node.map(closed);
  if (node === null || typeof node !== 'object') return node;
  const out = {};
  for (const [key, value] of Object.entries(node)) {
    if (key === '$ref' && typeof value === 'string') {
      out.$ref = value.replace('#/components/schemas/', '#/$defs/');
    } else if (key === 'properties') {
      out.properties = Object.fromEntries(
        Object.entries(value).map(([name, schema]) => [name, closed(schema)]),
      );
    } else {
      out[key] = closed(value);
    }
  }
  if (out.properties && out.additionalProperties === undefined) out.unevaluatedProperties = false;
  return out;
}

const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);
const defs = closed(doc.components?.schemas ?? {});
const wsErrors = [];
const messages = Object.entries(doc.components?.messages ?? {});
const expected = new Set(messages.map(([name]) => `${name}.json`));

for (const [name, message] of messages) {
  const fixture = join(wsDir, `${name}.json`);
  if (!existsSync(fixture)) {
    wsErrors.push(`ws/${name}.json: falta (el mensaje ${name} no tiene fixture)`);
    continue;
  }
  let value;
  try {
    value = JSON.parse(readFileSync(fixture, 'utf8'));
  } catch (e) {
    wsErrors.push(`ws/${name}.json: JSON inválido (${e.message})`);
    continue;
  }
  const validate = ajv.compile({ ...closed(message.payload), $defs: defs });
  if (!validate(value)) {
    const detail = validate.errors
      .map((e) => `${e.instancePath || '/'} ${e.message}${e.params?.unevaluatedProperty ? ` ('${e.params.unevaluatedProperty}')` : ''}`)
      .join('; ');
    wsErrors.push(`ws/${name}.json: ${detail}`);
  }
}

if (existsSync(wsDir)) {
  for (const entry of readdirSync(wsDir)) {
    if (entry.endsWith('.json') && !expected.has(entry)) {
      wsErrors.push(`ws/${entry}: huérfano (asyncapi.yaml no declara ese mensaje)`);
    }
  }
}

if (wsErrors.length) {
  console.error(`✖ ${wsErrors.length} problema(s) en los fixtures de frames:\n`);
  for (const e of wsErrors) console.error(`  - ${e}`);
  process.exit(1);
}
console.log(`✔ ${messages.length} fixtures de frames válidos en docs/api/fixtures/ws/`);
