#!/usr/bin/env bats
# Tests de infrastructure/deploy/render-web-headers.sh (T-I11; feature 001, T031 y T032)
# contra el _headers real de la web.

SCRIPT="$BATS_TEST_DIRNAME/../render-web-headers.sh"
TEMPLATE="$BATS_TEST_DIRNAME/../../../frontend/vito_app/web/_headers"

setup() {
  HEADERS="$BATS_TEST_TMPDIR/_headers"
  cp "$TEMPLATE" "$HEADERS"
  export VITO_API_BASE_URL=https://api.example.test/api/v1
  export VITO_WS_BASE_URL=wss://api.example.test
  export VITO_MEDIA_ORIGIN=https://media.example.test
}

csp() { grep -m1 'Content-Security-Policy' "$HEADERS"; }

@test "completa los tres orígenes y no deja marcadores" {
  run "$SCRIPT" "$HEADERS"
  [ "$status" -eq 0 ]
  [ -z "$(grep -o '_ORIGIN__' "$HEADERS")" ]
  [[ "$(csp)" == *"connect-src 'self' https://api.example.test wss://api.example.test https://media.example.test"* ]]
  [[ "$(csp)" == *"img-src 'self' data: blob: https://media.example.test;"* ]]
}

@test "quita la ruta de VITO_API_BASE_URL y conserva el puerto" {
  export VITO_API_BASE_URL=https://api.example.test:8443/api/v1
  run "$SCRIPT" "$HEADERS"
  [ "$status" -eq 0 ]
  [[ "$(csp)" == *" https://api.example.test:8443 "* ]]
  [[ "$(csp)" != *"/api/v1"* ]]
}

@test "la CSP de la plantilla mantiene las directivas de T-I11" {
  run "$SCRIPT" "$HEADERS"
  [ "$status" -eq 0 ]
  for d in "default-src 'self'" "script-src 'self' 'wasm-unsafe-eval'" "frame-ancestors 'none'" "object-src 'none'"; do
    [[ "$(csp)" == *"$d"* ]]
  done
  grep -q '^  Strict-Transport-Security: max-age=31536000; includeSubDomains$' "$HEADERS"
  grep -q '^  X-Frame-Options: DENY$' "$HEADERS"
}

@test "rechaza una API por http" {
  export VITO_API_BASE_URL=http://api.example.test/api/v1
  run "$SCRIPT" "$HEADERS"
  [ "$status" -eq 1 ]
  [[ "$output" == *VITO_API_BASE_URL* ]]
  grep -q '__API_ORIGIN__' "$HEADERS"
}

@test "rechaza un WS que no es wss" {
  export VITO_WS_BASE_URL=https://api.example.test
  run "$SCRIPT" "$HEADERS"
  [ "$status" -eq 1 ]
  [[ "$output" == *VITO_WS_BASE_URL* ]]
}

@test "rechaza VITO_MEDIA_ORIGIN vacía" {
  export VITO_MEDIA_ORIGIN=
  run "$SCRIPT" "$HEADERS"
  [ "$status" -eq 1 ]
  [[ "$output" == *VITO_MEDIA_ORIGIN* ]]
}

@test "rechaza un host con caracteres que romperían el reemplazo" {
  export VITO_MEDIA_ORIGIN='https://media.example.test|x'
  run "$SCRIPT" "$HEADERS"
  [ "$status" -eq 1 ]
}

@test "falla si queda un marcador que no conoce" {
  printf '  X-Test: __OTHER_ORIGIN__\n' >>"$HEADERS"
  run "$SCRIPT" "$HEADERS"
  [ "$status" -eq 1 ]
  [[ "$output" == *__OTHER_ORIGIN__* ]]
}

@test "sin argumentos imprime el uso y sale con 2" {
  run "$SCRIPT"
  [ "$status" -eq 2 ]
  [[ "$output" == *render-web-headers.sh* ]]
}
