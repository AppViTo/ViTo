#!/usr/bin/env bats
# Tests de infrastructure/deploy/pick-environment.sh (T055, research R5 y R8) con un `gh`
# falso que devuelve $FAKE_GH_OUT para GET /commits/{sha}/pulls.

load test_helper

SCRIPT="$BATS_TEST_DIRNAME/../pick-environment.sh"

setup() {
  setup_vito
  cat >"$FAKE_DIR/bin/gh" <<'EOF'
#!/usr/bin/env bash
printf '%s\n' "$*" >>"$FAKE_DIR/gh.log"
[[ "${FAKE_GH_RC:-0}" == 0 ]] || exit "$FAKE_GH_RC"
printf '%s\n' "${FAKE_GH_OUT:-[]}"
EOF
  chmod +x "$FAKE_DIR/bin/gh"
  export GITHUB_REPOSITORY=AppViTo/ViTo
}

pr() { # pr <rama de origen> <base> <merged_at|null>
  printf '{"head":{"ref":"%s"},"base":{"ref":"%s"},"merged_at":%s}' "$1" "$2" "$3"
}

@test "PR desde develop → release en production" {
  export FAKE_GH_OUT="[$(pr develop main '"2026-10-22T10:00:00Z"')]"
  run "$SCRIPT" "$(sha a)" deploy
  [ "$status" -eq 0 ]
  [ "${lines[0]}" = "kind=release" ]
  [ "${lines[1]}" = "environment=production" ]
  grep -q "repos/AppViTo/ViTo/commits/$(sha a)/pulls" "$FAKE_DIR/gh.log"
}

@test "PR desde hotfix/* → hotfix en production-hotfix" {
  export FAKE_GH_OUT="[$(pr hotfix/login-500 main '"2026-10-22T10:00:00Z"')]"
  run "$SCRIPT" "$(sha a)" deploy
  [ "${lines[0]}" = "kind=hotfix" ]
  [ "${lines[1]}" = "environment=production-hotfix" ]
}

@test "solo cuenta el PR mergeado a main (el merge main → develop del hotfix no)" {
  export FAKE_GH_OUT="[$(pr hotfix/x develop null),$(pr develop main '"2026-10-22T10:00:00Z"')]"
  run "$SCRIPT" "$(sha a)" deploy
  [ "${lines[1]}" = "environment=production" ]
}

@test "sin PR → production (la opción con prevent self-review)" {
  export FAKE_GH_OUT='[]'
  run "$SCRIPT" "$(sha a)" deploy
  [ "${lines[0]}" = "kind=release" ]
  [ "${lines[1]}" = "environment=production" ]
}

@test "si la API falla → production" {
  export FAKE_GH_RC=1
  run "$SCRIPT" "$(sha a)" deploy
  [ "$status" -eq 0 ]
  [ "${lines[-1]}" = "environment=production" ]
}

@test "mode=preflight → production-preflight sin consultar la API" {
  run "$SCRIPT" "$(sha a)" preflight
  [ "$status" -eq 0 ]
  [ "${lines[1]}" = "environment=production-preflight" ]
  [ ! -e "$FAKE_DIR/gh.log" ]
}

@test "mode desconocido o SHA inválido → exit 2" {
  run "$SCRIPT" "$(sha a)" otro
  [ "$status" -eq 2 ]
  run "$SCRIPT" main deploy
  [ "$status" -eq 2 ]
}
