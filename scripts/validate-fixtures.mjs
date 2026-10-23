#!/usr/bin/env node
// Valida docs/api/fixtures/ contra docs/api/openapi.yaml (T-C03).
//
// Reglas (docs/api/fixtures/README.md):
//   - cada operación con body JSON tiene <operationId>.request.json;
//   - cada status declarado tiene <operationId>.<status>.json; si el media type
//     declara `examples`, cada nombre tiene <operationId>.<ejemplo>.<status>.json;
//   - un status sin `content` tiene un fixture con el literal `null`;
//   - cada fixture valida contra su schema, sin propiedades no declaradas;
//   - no hay fixtures huérfanos (operación o status que el YAML no declara);
//   - cada operación tiene al menos un fixture 2xx y uno de error.
//
// Uso: node scripts/validate-fixtures.mjs   (o `npm run validate:fixtures`)

import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { parse } from 'yaml';

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..');
const SPEC_PATH = join(ROOT, 'docs/api/openapi.yaml');
const FIXTURES_DIR = join(ROOT, 'docs/api/fixtures');
const METHODS = ['get', 'put', 'post', 'delete', 'patch', 'head', 'options', 'trace'];
const JSON_MEDIA = 'application/json';

const spec = parse(readFileSync(SPEC_PATH, 'utf8'));
const errors = [];
const fail = (file, msg) => errors.push(`${file}: ${msg}`);

// Los schemas de OpenAPI 3.1 son JSON Schema 2020-12. Se copian a `$defs` para que
// `#/components/schemas/X` resuelva dentro de cada schema compilado, y a todo objeto
// con `properties` se le añade `unevaluatedProperties: false`: un campo que el
// contrato no declara (p. ej. un `tokenType` retirado) es un error del fixture.
function toValidatorSchema(node) {
  if (Array.isArray(node)) return node.map(toValidatorSchema);
  if (node === null || typeof node !== 'object') return node;
  const out = {};
  for (const [key, value] of Object.entries(node)) {
    if (key === '$ref' && typeof value === 'string') {
      out.$ref = value.replace('#/components/schemas/', '#/$defs/');
    } else if (key === 'properties') {
      out.properties = Object.fromEntries(
        Object.entries(value).map(([name, schema]) => [name, toValidatorSchema(schema)]),
      );
    } else {
      out[key] = toValidatorSchema(value);
    }
  }
  if (out.properties && out.additionalProperties === undefined && out.unevaluatedProperties === undefined) {
    out.unevaluatedProperties = false;
  }
  return out;
}

const defs = toValidatorSchema(spec.components?.schemas ?? {});
// strict: false porque OpenAPI añade palabras clave que JSON Schema no conoce
// (`example`, `xml`, `discriminator`…).
const ajv = new Ajv2020({ strict: false, allErrors: true });
addFormats(ajv);

function compile(schema) {
  return ajv.compile({ ...toValidatorSchema(schema), $defs: defs });
}

function readFixture(file) {
  try {
    return { ok: true, value: JSON.parse(readFileSync(file, 'utf8')) };
  } catch (e) {
    return { ok: false, error: e.message };
  }
}

function describeErrors(validate) {
  return validate.errors
    .map((e) => {
      const at = e.instancePath || '/';
      const extra = e.params?.unevaluatedProperty ? ` ('${e.params.unevaluatedProperty}')` : '';
      return `${at} ${e.message}${extra}`;
    })
    .join('; ');
}

// --- Fixtures esperados según el YAML -------------------------------------
// clave: ruta relativa a FIXTURES_DIR → { schema | null, opId }
const expected = new Map();
const operations = [];

for (const [path, item] of Object.entries(spec.paths ?? {})) {
  for (const method of METHODS) {
    const op = item[method];
    if (!op) continue;
    const label = `${method.toUpperCase()} ${path}`;
    if (!op.operationId || !op.tags?.length) {
      fail(label, 'sin operationId o tags: no se puede resolver su fixture');
      continue;
    }
    const folder = op.tags[0].toLowerCase().replace(/\s+/g, '-');
    const base = `${folder}/${op.operationId}`;
    operations.push({ base, label });

    const reqSchema = op.requestBody?.content?.[JSON_MEDIA]?.schema;
    if (reqSchema) expected.set(`${base}.request.json`, { schema: reqSchema });

    for (const [status, response] of Object.entries(op.responses ?? {})) {
      const content = response.content;
      const media = content?.[JSON_MEDIA];
      if (!content) {
        expected.set(`${base}.${status}.json`, { schema: null });
      } else if (media?.schema) {
        // Variantes declaradas en `examples`: un fixture por nombre, sin el
        // `<operationId>.<status>.json` canónico (README §Variantes).
        const examples = media.examples;
        if (examples && Object.keys(examples).length > 0) {
          for (const name of Object.keys(examples)) {
            expected.set(`${base}.${name}.${status}.json`, { schema: media.schema });
          }
        } else {
          expected.set(`${base}.${status}.json`, { schema: media.schema });
        }
      } else {
        fail(label, `status ${status} no declara ${JSON_MEDIA}; el validador no sabe qué aplicar`);
      }
    }
  }
}

// --- Fixtures presentes en disco ------------------------------------------
function listJson(dir) {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) return listJson(full);
    return name.endsWith('.json') ? [full] : [];
  });
}

const present = new Set(listJson(FIXTURES_DIR).map((f) => relative(FIXTURES_DIR, f).replaceAll('\\', '/')));

for (const file of present) {
  if (!expected.has(file)) fail(file, 'huérfano: el YAML no declara esa operación o ese status');
}

let validated = 0;
for (const [file, { schema }] of expected) {
  if (!present.has(file)) {
    fail(file, 'falta: el YAML lo declara y no hay fixture');
    continue;
  }
  const parsed = readFixture(join(FIXTURES_DIR, file));
  if (!parsed.ok) {
    fail(file, `JSON inválido (${parsed.error})`);
    continue;
  }
  if (schema === null) {
    if (parsed.value !== null) fail(file, 'el status no tiene cuerpo en el YAML: el fixture debe ser `null`');
  } else {
    const validate = compile(schema);
    if (!validate(parsed.value)) fail(file, describeErrors(validate));
  }
  validated++;
}

for (const { base, label } of operations) {
  const statuses = [...present]
    .filter((file) => file.startsWith(`${base}.`))
    .map((file) => Number(file.match(/\.(\d{3})\.json$/)?.[1]))
    .filter((status) => !Number.isNaN(status));
  const has = (pred) => statuses.some(pred);
  if (!has((s) => s >= 200 && s < 300)) fail(label, 'sin fixture 2xx');
  if (!has((s) => s >= 400)) fail(label, 'sin fixture de error (4xx/5xx)');
}

if (errors.length) {
  console.error(`✖ ${errors.length} problema(s) en los fixtures del contrato:\n`);
  for (const e of errors) console.error(`  - ${e}`);
  process.exit(1);
}
console.log(`✔ ${validated} fixtures válidos para ${operations.length} operaciones de openapi.yaml`);
