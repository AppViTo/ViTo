#!/usr/bin/env bats
# ensure-service-users.sh y service-users.js contra un MongoDB real (misma versión que
# docker-compose.prod.yml), T-I19: cada vito_<svc> lee y escribe su base y nada más; el
# usuario de event-service no puede leer la base de auth (vito_db). También el camino del
# initdb para un volumen vacío. Corre en CI (Deploy Scripts, VITO_TEST_MONGO=1); sin eso se omite.

REMOTE="$BATS_TEST_DIRNAME/../remote"
NAME=vito-test-mongo-users
INIT=vito-test-mongo-initdb
ROOT_PW=root-test-only
SERVICES=(AUTH SOCIAL EVENT CHECKIN MOSAIC STREAMING ARCHIVE CHAT MEDIA)

wait_mongo() {
  local i
  for i in $(seq 1 60); do
    docker exec "$1" mongosh --quiet -u vito_admin -p "$ROOT_PW" --eval 'db.adminCommand("ping").ok' \
      2>/dev/null | grep -q 1 && return 0
    sleep 2
  done
  echo "mongo $1 no arrancó" >&3
  return 1
}

setup_file() {
  [[ "${VITO_TEST_MONGO:-}" == 1 ]] || return 0
  docker rm -f "$NAME" "$INIT" >/dev/null 2>&1 || true
  # Las mismas etiquetas que pone Compose: así lo encuentra el script
  docker run -d --name "$NAME" \
    --label com.docker.compose.project=vito --label com.docker.compose.service=mongodb \
    -e MONGO_INITDB_ROOT_USERNAME=vito_admin -e MONGO_INITDB_ROOT_PASSWORD="$ROOT_PW" \
    mongo:7.0.14 >/dev/null
  wait_mongo "$NAME"
}

teardown_file() {
  [[ "${VITO_TEST_MONGO:-}" == 1 ]] && docker rm -f "$NAME" "$INIT" >/dev/null 2>&1 || true
}

setup() {
  [[ "${VITO_TEST_MONGO:-}" == 1 ]] || skip "VITO_TEST_MONGO=1 para correr contra Mongo real"
  export VITO_ROOT="$BATS_TEST_TMPDIR/vito"
  mkdir -p "$VITO_ROOT"
  {
    printf 'MONGO_ROOT_USERNAME=vito_admin\nMONGO_ROOT_PASSWORD=%s\n' "$ROOT_PW"
    local s
    for s in "${SERVICES[@]}"; do printf 'MONGO_%s_PASSWORD=pw%s\n' "$s" "${s,,}"; done
  } >"$VITO_ROOT/.env"
}

# as_user <contenedor> <usuario> <contraseña> <base> <js>: corre js autenticado como ese usuario
as_user() {
  docker exec "$1" mongosh --quiet "mongodb://$2:$3@127.0.0.1:27017/$4?authSource=admin" --eval "$5"
}

@test "crea los 9 usuarios: cada uno lee y escribe su base" {
  run "$REMOTE/ensure-service-users.sh"
  [ "$status" -eq 0 ]
  [[ "$output" == *"9 usuarios listos"* ]]
  [[ "$output" != *pwevent* ]]
  run as_user "$NAME" vito_event pwevent vito_events 'db.t.insertOne({a: 1}); print(db.t.countDocuments())'
  [ "$status" -eq 0 ]
  [[ "$output" == *1* ]]
  run as_user "$NAME" vito_chat pwchat vito_chat 'db.chat_rooms.insertOne({a: 1}); print("ok")'
  [ "$status" -eq 0 ]
}

@test "chat y media ya no pueden leer la base de auth (T-B40)" {
  run "$REMOTE/ensure-service-users.sh"
  [ "$status" -eq 0 ]
  run as_user "$NAME" vito_chat pwchat vito_chat 'db.getSiblingDB("vito_db").users.findOne()'
  [ "$status" -ne 0 ]
  run as_user "$NAME" vito_media pwmedia vito_media 'db.getSiblingDB("vito_db").users.findOne()'
  [ "$status" -ne 0 ]
}

@test "el usuario de event-service no puede leer la base de auth (vito_db)" {
  run "$REMOTE/ensure-service-users.sh"
  [ "$status" -eq 0 ]
  run as_user "$NAME" vito_event pwevent vito_events 'db.getSiblingDB("vito_db").users.findOne()'
  [ "$status" -ne 0 ]
  [[ "$output" == *"not authorized"* || "$output" == *Unauthorized* ]]
}

@test "ningún servicio escribe en la base de otro" {
  run "$REMOTE/ensure-service-users.sh"
  [ "$status" -eq 0 ]
  run as_user "$NAME" vito_social pwsocial vito_social 'db.getSiblingDB("vito_events").events.insertOne({x: 1})'
  [ "$status" -ne 0 ]
  run as_user "$NAME" vito_auth pwauth vito_db 'db.getSiblingDB("vito_mosaic").items.insertOne({x: 1})'
  [ "$status" -ne 0 ]
}

@test "idempotente y rota la contraseña" {
  run "$REMOTE/ensure-service-users.sh"
  [ "$status" -eq 0 ]
  sed -i 's/^MONGO_EVENT_PASSWORD=.*/MONGO_EVENT_PASSWORD=rotada1/' "$VITO_ROOT/.env"
  run "$REMOTE/ensure-service-users.sh"
  [ "$status" -eq 0 ]
  run as_user "$NAME" vito_event rotada1 vito_events 'print(db.t.countDocuments())'
  [ "$status" -eq 0 ]
  run as_user "$NAME" vito_event pwevent vito_events 'print(db.t.countDocuments())'
  [ "$status" -ne 0 ]
}

@test "falta una contraseña: sale con 2 sin tocar nada y nombra solo la variable" {
  sed -i '/^MONGO_MEDIA_PASSWORD=/d' "$VITO_ROOT/.env"
  run "$REMOTE/ensure-service-users.sh"
  [ "$status" -eq 2 ]
  [[ "$output" == *MONGO_MEDIA_PASSWORD* ]]
  [[ "$output" != *pwauth* ]]
}

@test "contraseña no alfanumérica: falla (rompería MONGODB_URI) sin imprimir el valor" {
  sed -i 's/^MONGO_CHAT_PASSWORD=.*/MONGO_CHAT_PASSWORD=con@arroba/' "$VITO_ROOT/.env"
  run "$REMOTE/ensure-service-users.sh"
  [ "$status" -eq 1 ]
  [[ "$output" == *MONGO_CHAT_PASSWORD* ]]
  [[ "$output" != *arroba* ]]
}

@test "volumen vacío: el initdb de mongodb crea los usuarios antes de que arranque nada" {
  local args=() s
  for s in "${SERVICES[@]}"; do args+=(-e "MONGO_${s}_PASSWORD=init${s,,}"); done
  docker run -d --name "$INIT" \
    -e MONGO_INITDB_ROOT_USERNAME=vito_admin -e MONGO_INITDB_ROOT_PASSWORD="$ROOT_PW" "${args[@]}" \
    -v "$REMOTE/service-users.js:/docker-entrypoint-initdb.d/10-service-users.js:ro" \
    mongo:7.0.14 >/dev/null
  # El ping ya responde mientras corre initdb: esperar a que el entrypoint termine
  local i
  for i in $(seq 1 60); do
    docker logs "$INIT" 2>&1 | grep -q "MongoDB init process complete" && break
    sleep 2
  done
  wait_mongo "$INIT"
  run as_user "$INIT" vito_media initmedia vito_media 'db.media.insertOne({a: 1}); print("ok")'
  [ "$status" -eq 0 ]
  run as_user "$INIT" vito_media initmedia vito_media 'db.getSiblingDB("vito_events").events.findOne()'
  [ "$status" -ne 0 ]
}
