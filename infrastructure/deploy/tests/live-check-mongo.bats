#!/usr/bin/env bats
# live-check.sh, live-check.js y ensure-ro-user.sh contra un MongoDB real (misma versión
# que docker-compose.prod.yml). Verifica la consulta de research R6 y que vito_deploy_ro
# solo pueda leer. Corre en CI (Deploy Scripts, VITO_TEST_MONGO=1); sin eso se omite.

REMOTE="$BATS_TEST_DIRNAME/../remote"
NAME=vito-test-mongo
ROOT_PW=root-test-only

setup_file() {
  [[ "${VITO_TEST_MONGO:-}" == 1 ]] || return 0
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  # Las mismas etiquetas que pone Compose: así lo encuentran los scripts
  docker run -d --name "$NAME" \
    --label com.docker.compose.project=vito --label com.docker.compose.service=mongodb \
    -e MONGO_INITDB_ROOT_USERNAME=vito_admin -e MONGO_INITDB_ROOT_PASSWORD="$ROOT_PW" \
    mongo:7.0.14 >/dev/null
  local i
  for i in $(seq 1 60); do
    docker exec "$NAME" mongosh --quiet -u vito_admin -p "$ROOT_PW" --eval 'db.adminCommand("ping").ok' \
      2>/dev/null | grep -q 1 && return 0
    sleep 2
  done
  echo "mongo no arrancó" >&3
  return 1
}

teardown_file() {
  [[ "${VITO_TEST_MONGO:-}" == 1 ]] && docker rm -f "$NAME" >/dev/null 2>&1 || true
}

setup() {
  [[ "${VITO_TEST_MONGO:-}" == 1 ]] || skip "VITO_TEST_MONGO=1 para correr contra Mongo real"
  export VITO_ROOT="$BATS_TEST_TMPDIR/vito"
  mkdir -p "$VITO_ROOT/releases/x"
  ln -s releases/x "$VITO_ROOT/current"
  printf 'MONGO_ROOT_USERNAME=vito_admin\nMONGO_ROOT_PASSWORD=%s\n' "$ROOT_PW" >"$VITO_ROOT/.env"
  export MONGO_DEPLOY_RO_PASSWORD=ro-test-only
  run "$REMOTE/ensure-ro-user.sh"
  [ "$status" -eq 0 ]
  root_eval 'db.getSiblingDB("vito_events").events.deleteMany({}); db.getSiblingDB("vito_streaming").live_streams.deleteMany({})'
}

root_eval() {
  docker exec "$NAME" mongosh --quiet -u vito_admin -p "$ROOT_PW" --eval "$1" >/dev/null
}

# event <status> <inicio en minutos desde ahora> <fin en minutos desde ahora>
event() {
  root_eval "db.getSiblingDB('vito_events').events.insertOne({title: 'Evento $1 $2', status: '$1',
    details: {startDate: new Date(Date.now() + ($2) * 60000), endDate: new Date(Date.now() + ($3) * 60000)}})"
}

stream() {
  root_eval "db.getSiblingDB('vito_streaming').live_streams.insertOne({eventId: 'e1', status: '$1'})"
}

@test "ensure-ro-user es idempotente" {
  run "$REMOTE/ensure-ro-user.sh"
  [ "$status" -eq 0 ]
}

@test "sin datos: libre" {
  run "$REMOTE/live-check.sh"
  [ "$status" -eq 0 ]
  [ "$output" = '{"events":[],"streams":[]}' ]
}

@test "evento PUBLISHED en curso: bloquea con su título" {
  event PUBLISHED -30 120
  run "$REMOTE/live-check.sh"
  [ "$status" -eq 3 ]
  [[ "$output" == *'"title":"Evento PUBLISHED -30"'* ]]
}

@test "evento que empieza dentro de 1 h: bloquea; dentro de 3 h: no" {
  event PUBLISHED 45 200
  run "$REMOTE/live-check.sh"
  [ "$status" -eq 3 ]
  root_eval 'db.getSiblingDB("vito_events").events.deleteMany({})'
  event PUBLISHED 180 300
  run "$REMOTE/live-check.sh"
  [ "$status" -eq 0 ]
}

@test "evento que terminó hace 30 min: bloquea; hace 3 h: no" {
  event PUBLISHED -240 -30
  run "$REMOTE/live-check.sh"
  [ "$status" -eq 3 ]
  root_eval 'db.getSiblingDB("vito_events").events.deleteMany({})'
  event PUBLISHED -400 -180
  run "$REMOTE/live-check.sh"
  [ "$status" -eq 0 ]
}

@test "evento en la ventana pero no publicado: no bloquea" {
  event DRAFT -30 120
  event CANCELLED -30 120
  run "$REMOTE/live-check.sh"
  [ "$status" -eq 0 ]
}

@test "stream LIVE, CONNECTING o BUFFERING bloquea; ENDED no" {
  stream ENDED
  run "$REMOTE/live-check.sh"
  [ "$status" -eq 0 ]
  local s
  for s in LIVE CONNECTING BUFFERING; do
    root_eval 'db.getSiblingDB("vito_streaming").live_streams.deleteMany({})'
    stream "$s"
    run "$REMOTE/live-check.sh"
    [ "$status" -eq 3 ]
    [[ "$output" == *'"eventId":"e1"'* ]]
  done
}

@test "contraseña incorrecta: no se pudo comprobar (7)" {
  MONGO_DEPLOY_RO_PASSWORD=otra run "$REMOTE/live-check.sh"
  [ "$status" -eq 7 ]
}

@test "vito_deploy_ro solo lee: no puede escribir ni leer otras bases" {
  run docker exec "$NAME" mongosh --quiet -u vito_deploy_ro -p ro-test-only --authenticationDatabase admin \
    --eval 'db.getSiblingDB("vito_events").events.insertOne({x: 1})'
  [ "$status" -ne 0 ]
  run docker exec "$NAME" mongosh --quiet -u vito_deploy_ro -p ro-test-only --authenticationDatabase admin \
    --eval 'db.getSiblingDB("vito_db").users.findOne()'
  [ "$status" -ne 0 ]
}
