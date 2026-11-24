// Utilidades de esquema compartidas por validate-asyncapi.mjs y mock-ws.mjs (T-C05).

/**
 * Copia de un esquema de asyncapi.yaml con los objetos cerrados
 * (`unevaluatedProperties: false` donde hay `properties` y no se dijo otra cosa) y
 * los `$ref` a `#/components/schemas/` reescritos a `#/$defs/`. Así un campo que el
 * contrato no declara (p. ej. la identidad en un frame del cliente) no valida.
 */
export function closed(node) {
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
