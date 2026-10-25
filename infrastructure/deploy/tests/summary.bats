#!/usr/bin/env bats
# Tests de infrastructure/deploy/summary.sh: resumen del despliegue en Markdown.

load test_helper

SCRIPT="$BATS_TEST_DIRNAME/../summary.sh"

@test "resumen con resultado, SHA corto, servicios y versiones" {
  rec=$(printf '{"type":"deploy","kind":"release","sha":"%s","from_sha":null,"versions":{"auth-service":"%s"},"changed":["auth-service"],"approver":"lead1","triggered_by":"lead2","reason":null,"forced_live_event":false,"result":"succeeded","exit_code":0,"duration_s":42}' "$(sha a)" "$(sha a)")
  run "$SCRIPT" <<<"$rec"
  [ "$status" -eq 0 ]
  [[ "$output" == *"deploy · succeeded (código 0)"* ]]
  [[ "$output" == *"| Release | \`aaaaaaaaaaaa\` |"* ]]
  [[ "$output" == *"| Anterior | \`—\` |"* ]]
  [[ "$output" == *"| Servicios reconstruidos | auth-service |"* ]]
  [[ "$output" == *"| auth-service | \`aaaaaaaaaaaa\` |"* ]]
}

@test "rollback sin servicios reconstruidos" {
  run "$SCRIPT" <<<'{"type":"rollback","kind":null,"sha":"x","from_sha":"y","versions":{},"changed":[],"result":"rolled_back","exit_code":5,"duration_s":1,"forced_live_event":true}'
  [ "$status" -eq 0 ]
  [[ "$output" == *"| Servicios reconstruidos | ninguno |"* ]]
  [[ "$output" == *"| Forzado en evento en vivo | sí |"* ]]
}
