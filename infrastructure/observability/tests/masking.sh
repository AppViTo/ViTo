#!/usr/bin/env bash
# T034: comprueba que la etapa de enmascarado de config.alloy no deja pasar
# Bearer, token de query, contraseñas, JWT ni correos (FR-026).
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
ALLOY_IMAGE=${ALLOY_IMAGE:-grafana/alloy:v1.21.3}
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

cat >"$tmp/input.log" <<'EOF'
{"level":"INFO","service":"auth-service","msg":"Authorization: Bearer abc123"}
{"level":"WARN","service":"api-gateway","msg":"GET /ws?token=abc123&x=1"}
{"level":"ERROR","service":"auth-service","password":"x","user":"ops@example.test"}
{"level":"ERROR","service":"auth-service","msg":"{\"password\":\"x\"}"}
{"level":"INFO","service":"checkin-service","msg":"jwt eyJhbGciOi.eyJzdWIi.c2ln"}
{"level":"INFO","service":"event-service","msg":"contact ops@example.test"}
EOF

python3 - "$ROOT/config.alloy" "$tmp/input.log" "$tmp/mask-test.alloy" <<'PY'
import sys
from pathlib import Path

config, source, output = map(Path, sys.argv[1:])
text = config.read_text()
start = text.index('loki.process "mask" {')
depth = 0
end = None
for i in range(start, len(text)):
    if text[i] == '{':
        depth += 1
    elif text[i] == '}':
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end is None:
    raise SystemExit('no se encontró el bloque loki.process "mask"')

block = text[start:end].replace(
    'forward_to = [loki.write.grafana.receiver]',
    'forward_to = [loki.echo.test.receiver]',
)
output.write_text(
    'loki.source.file "test" {\n'
    f'  targets = [{{ __path__ = "{source}" }}]\n'
    '  forward_to = [loki.process.mask.receiver]\n'
    '}\n\n'
    f'{block}\n\n'
    'loki.echo "test" {}\n'
)
PY

if [[ -n ${ALLOY_BIN:-} ]]; then
  alloy=("$ALLOY_BIN")
else
  alloy=(docker run --rm -v "$tmp:$tmp" -w "$tmp" "$ALLOY_IMAGE")
fi

"${alloy[@]}" validate "$tmp/mask-test.alloy"

out=$tmp/alloy.log
rc=0
timeout 8s "${alloy[@]}" run --server.http.listen-addr=127.0.0.1:12399 \
  --storage.path="$tmp/data" "$tmp/mask-test.alloy" >"$out" 2>&1 || rc=$?
# timeout termina con 124 cuando Alloy ya leyó el archivo y quedó en primer plano.
if [[ $rc -ne 0 && $rc -ne 124 ]]; then
  cat "$out"
  exit "$rc"
fi

if grep -Eq 'Bearer abc123|token=abc123|"password":"x"|eyJhbGciOi\.eyJzdWIi\.c2ln|ops@example\.test' "$out"; then
  echo "✗ el enmascarado dejó pasar un secreto" >&2
  grep -E 'received log entry' "$out" >&2 || true
  exit 1
fi

for expected in \
  'Bearer \[REDACTED\]' \
  'token=\[REDACTED\]' \
  '\\"password\\":\\"\[REDACTED\]\\"' \
  '\[REDACTED_JWT\]' \
  '\[REDACTED_EMAIL\]'; do
  if ! grep -Eq "$expected" "$out"; then
    echo "✗ falta el valor enmascarado: $expected" >&2
    grep -E 'received log entry' "$out" >&2 || true
    exit 1
  fi
done

echo "✓ enmascarado de logs correcto"
