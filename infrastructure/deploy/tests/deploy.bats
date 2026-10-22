#!/usr/bin/env bats
# Tests de infrastructure/deploy/remote/deploy.sh (T007) con un docker falso.
# Contrato: specs/001-cd-produccion/contracts/remote-scripts.md

load test_helper

setup() { setup_vito; }

A=$(sha a)
B=$(sha b)
C=$(sha c)
D=$(sha d)

@test "(a) apply copia el manifiesto activo y solo cambia --changed, más DEPLOYED_SHA" {
  deploy_ok "$A"
  make_bundle "$B"
  load_images "$B" auth-service
  run "$DEPLOY" apply "$B" --changed "auth-service"
  [ "$status" -eq 0 ]
  run versions_of "$B"
  [[ "$output" == *"TAG_AUTH_SERVICE=$B"* ]]
  [[ "$output" == *"TAG_EVENT_SERVICE=$A"* ]]
  [[ "$output" == *"TAG_MONGO_BACKUP=$A"* ]]
  [[ "$output" == *"DEPLOYED_SHA=$B"* ]]
  [ "$(current_sha)" = "$B" ]
}

@test "(b) primer despliegue: las 13 TAG_* valen el SHA" {
  deploy_ok "$A"
  run grep -c "^TAG_.*=$A\$" "$VITO_ROOT/releases/$A/versions.env"
  [ "$output" -eq 13 ]
}

@test "(c) config -q fallido sale con 2 sin cambiar current" {
  deploy_ok "$A"
  make_bundle "$B"
  load_images "$B"
  FAKE_CONFIG_RC=1 run "$DEPLOY" apply "$B" --changed "auth-service"
  [ "$status" -eq 2 ]
  [ "$(current_sha)" = "$A" ]
  # el único up es el del primer despliegue
  [ "$(grep -c ' up ' "$DOCKER_LOG")" -eq 1 ]
}

@test "(d) imagen faltante sale con 4 sin cambiar current" {
  deploy_ok "$A"
  make_bundle "$B"
  run "$DEPLOY" apply "$B" --changed "auth-service"
  [ "$status" -eq 4 ]
  [[ "$output" == *"vito/auth-service:$B"* ]]
  [ "$(current_sha)" = "$A" ]
}

@test "(e) salud fallida con anterior: current vuelve al anterior y sale con 5" {
  export FAKE_UP_RCS="0 1 0"
  deploy_ok "$A"
  make_bundle "$B"
  load_images "$B"
  run "$DEPLOY" apply "$B" --changed "auth-service"
  [ "$status" -eq 5 ]
  [ "$(current_sha)" = "$A" ]
  [ "$(grep -c ' up ' "$DOCKER_LOG")" -eq 3 ]
}

@test "(e2) salud fallida y la vuelta también falla: sale con 6" {
  export FAKE_UP_RCS="0 1 1"
  deploy_ok "$A"
  make_bundle "$B"
  load_images "$B"
  run "$DEPLOY" apply "$B" --changed "auth-service"
  [ "$status" -eq 6 ]
}

@test "(f) salud fallida sin anterior sale con 6" {
  export FAKE_UP_RCS="1"
  make_bundle "$A"
  load_images "$A"
  run "$DEPLOY" apply "$A"
  [ "$status" -eq 6 ]
}

@test "(g) poda: quedan los 3 últimos bundles con éxito y sus imágenes" {
  deploy_ok "$A"
  deploy_ok "$B" --changed "${SERVICES[*]}"
  deploy_ok "$C" --changed "${SERVICES[*]}"
  deploy_ok "$D" --changed "${SERVICES[*]}"
  run ls "$VITO_ROOT/releases"
  [ "${#lines[@]}" -eq 3 ]
  [ ! -d "$VITO_ROOT/releases/$A" ]
  ! grep -q ":$A\$" "$FAKE_DIR/images"
  grep -q "vito/auth-service:$B" "$FAKE_DIR/images"
}

@test "(g2) la poda no borra imágenes que un bundle retenido todavía usa" {
  deploy_ok "$A"
  deploy_ok "$B" --changed "auth-service"
  deploy_ok "$C" --changed "auth-service"
  deploy_ok "$D" --changed "auth-service"
  # event-service sigue en la versión de A en los tres bundles retenidos
  grep -q "vito/event-service:$A" "$FAKE_DIR/images"
  ! grep -q "vito/auth-service:$A" "$FAKE_DIR/images"
}

@test "(h) rollback a un SHA no retenido sale con 4" {
  deploy_ok "$A"
  run "$DEPLOY" rollback "$B"
  [ "$status" -eq 4 ]
  [ "$(current_sha)" = "$A" ]
}

@test "(h2) rollback a un SHA retenido lo activa sin reconstruir" {
  deploy_ok "$A"
  deploy_ok "$B" --changed "auth-service"
  run "$DEPLOY" rollback "$A" --meta '{"triggered_by":"lead","reason":"prueba"}'
  [ "$status" -eq 0 ]
  [ "$(current_sha)" = "$A" ]
  [ "$(tail -1 "$VITO_ROOT/history")" = "$A" ]
}

@test "(i) cada ejecución agrega una línea JSON válida con los campos de data-model" {
  deploy_ok "$A" --meta '{"approver":"lead1","triggered_by":"lead2","run_url":"https://example.test/run/1","kind":"release"}'
  make_bundle "$B"
  FAKE_CONFIG_RC=1 run "$DEPLOY" apply "$B"
  [ "$status" -eq 2 ]
  [ "$(wc -l <"$VITO_ROOT/deployments.log")" -eq 2 ]
  python3 - "$VITO_ROOT/deployments.log" <<'PY'
import json, sys
lines = open(sys.argv[1]).read().splitlines()
need = {"at", "type", "kind", "sha", "from_sha", "versions", "changed", "approver",
        "triggered_by", "reason", "forced_live_event", "result", "duration_s", "run_url"}
for line in lines:
    rec = json.loads(line)
    missing = need - rec.keys()
    assert not missing, missing
first, second = (json.loads(l) for l in lines)
assert first["result"] == "succeeded" and first["approver"] == "lead1", first
assert first["from_sha"] is None and len(first["versions"]) == 13, first
assert second["result"] == "failed" and second["exit_code"] == 2, second
assert second["from_sha"] == first["sha"], second
PY
}

@test "(i2) --meta que no es JSON sale con 2 sin tocar nada" {
  make_bundle "$A"
  load_images "$A"
  run "$DEPLOY" apply "$A" --meta 'no-es-json'
  [ "$status" -eq 2 ]
  [ ! -e "$VITO_ROOT/current" ]
}

@test "(j) CFG_CADDY no cambia si el Caddyfile es igual y cambia si no" {
  deploy_ok "$A"
  deploy_ok "$B" --changed "auth-service"
  make_bundle "$C" "caddy distinto"
  load_images "$C" auth-service
  run "$DEPLOY" apply "$C" --changed "auth-service"
  [ "$status" -eq 0 ]
  cfg() { sed -n 's/^CFG_CADDY=//p' "$VITO_ROOT/releases/$1/versions.env"; }
  [ -n "$(cfg "$A")" ]
  [ "$(cfg "$A")" = "$(cfg "$B")" ]
  [ "$(cfg "$B")" != "$(cfg "$C")" ]
}

@test "(k) toda llamada a compose lleva -p vito y --project-directory current" {
  deploy_ok "$A"
  deploy_ok "$B" --changed "auth-service"
  run "$DEPLOY" status
  [ "$status" -eq 0 ]
  grep -q '^compose ' "$DOCKER_LOG"
  ! grep '^compose ' "$DOCKER_LOG" | grep -v -- "-p vito --project-directory $VITO_ROOT/current "
}

@test "(l) check valida sin cambiar current ni llamar a up" {
  deploy_ok "$A"
  make_bundle "$B"
  : >"$DOCKER_LOG"
  run "$DEPLOY" check "$B"
  [ "$status" -eq 0 ]
  [ "$(current_sha)" = "$A" ]
  grep -q ' config -q' "$DOCKER_LOG"
  ! grep -q ' up ' "$DOCKER_LOG"
  [ ! -e "$VITO_ROOT/releases/$B/versions.env" ]
}

@test "(l2) check con configuración inválida sale con 2" {
  deploy_ok "$A"
  make_bundle "$B"
  FAKE_CONFIG_RC=1 run "$DEPLOY" check "$B"
  [ "$status" -eq 2 ]
}

@test "(m) versions.env incluye RELEASE_VERSION y los CFG_*" {
  deploy_ok "$A"
  run versions_of "$A"
  [[ "$output" == *"RELEASE_VERSION=0.1.0"* ]]
  [[ "$output" == *"CFG_SRS="* ]]
  [[ "$output" == *"CFG_ALLOY=none"* ]]
}

@test "servicio desconocido en --changed sale con 2" {
  make_bundle "$A"
  run "$DEPLOY" apply "$A" --changed "no-existe"
  [ "$status" -eq 2 ]
}

@test "SHA con formato inválido sale con 2" {
  run "$DEPLOY" apply "../../etc"
  [ "$status" -eq 2 ]
}

@test "status sin despliegue no falla" {
  run "$DEPLOY" status
  [ "$status" -eq 0 ]
}
