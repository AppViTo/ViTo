#!/usr/bin/env bats
# migrate-service-dbs.sh y service-dbs.js contra un MongoDB real (misma versión que
# docker-compose.prod.yml), T-B40: chat y media salen de vito_db a vito_chat / vito_media con
# sus documentos e índices, y vuelven con `shared` (rollback). Corre en CI (Deploy Scripts,
# VITO_TEST_MONGO=1); sin eso se omite.

REMOTE="$BATS_TEST_DIRNAME/../remote"
NAME=vito-test-mongo-dbs
ROOT_PW=root-test-only

setup_file() {
  [[ "${VITO_TEST_MONGO:-}" == 1 ]] || return 0
  docker rm -f "$NAME" >/dev/null 2>&1 || true
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

# root <js>: corre js como root
root() {
  docker exec "$NAME" mongosh --quiet -u vito_admin -p "$ROOT_PW" --eval "$1"
}

setup() {
  [[ "${VITO_TEST_MONGO:-}" == 1 ]] || skip "VITO_TEST_MONGO=1 para correr contra Mongo real"
  export VITO_ROOT="$BATS_TEST_TMPDIR/vito"
  mkdir -p "$VITO_ROOT"
  printf 'MONGO_ROOT_USERNAME=vito_admin\nMONGO_ROOT_PASSWORD=%s\n' "$ROOT_PW" >"$VITO_ROOT/.env"
  # Estado anterior a T-B40: todo en vito_db, con un índice y la colección de auth al lado
  root '
    for (const d of ["vito_db", "vito_chat", "vito_media"]) db.getSiblingDB(d).dropDatabase();
    const v = db.getSiblingDB("vito_db");
    v.users.insertOne({email: "a@b.c"});
    v.chat_messages.insertMany([{room: 1}, {room: 2}]);
    v.chat_messages.createIndex({room: 1}, {name: "room_1"});
    v.chat_rooms.insertOne({name: "r"});
    v.media.insertMany([{k: 1}, {k: 2}, {k: 3}]);
  ' >/dev/null
}

# counts: "<vito_db chat_messages> <vito_chat chat_messages> <vito_media media> <vito_db users>"
counts() {
  root '
    const c = (d, col) => db.getSiblingDB(d).getCollection(col).countDocuments();
    print([c("vito_db", "chat_messages"), c("vito_chat", "chat_messages"),
           c("vito_media", "media"), c("vito_db", "users")].join(" "));
  '
}

@test "split mueve chat y media con sus documentos e índices; users se queda en vito_db" {
  run "$REMOTE/migrate-service-dbs.sh" split
  [ "$status" -eq 0 ]
  [[ "$output" == *"3 colecciones listas (split)"* ]]
  [[ "$output" != *"$ROOT_PW"* ]]
  [ "$(counts)" = "0 2 3 1" ]
  run root 'print(db.getSiblingDB("vito_chat").chat_messages.getIndexes().map(i => i.name).join(","))'
  [[ "$output" == *room_1* ]]
  run root 'print(db.getSiblingDB("vito_db").getCollectionNames().sort().join(","))'
  [ "$output" = users ]
}

@test "idempotente: la segunda vez no hay nada que mover" {
  "$REMOTE/migrate-service-dbs.sh" split
  run "$REMOTE/migrate-service-dbs.sh" split
  [ "$status" -eq 0 ]
  [[ "$output" == *"nada que mover (split)"* ]]
  [ "$(counts)" = "0 2 3 1" ]
}

@test "shared devuelve las colecciones a vito_db (rollback a un release anterior)" {
  "$REMOTE/migrate-service-dbs.sh" split
  run "$REMOTE/migrate-service-dbs.sh" shared
  [ "$status" -eq 0 ]
  [ "$(counts)" = "2 0 0 1" ]
  run root 'print(db.getSiblingDB("vito_db").chat_messages.getIndexes().map(i => i.name).join(","))'
  [[ "$output" == *room_1* ]]
}

@test "destino vacío creado al arrancar: se reemplaza y se mueve" {
  root 'db.getSiblingDB("vito_chat").createCollection("chat_messages")' >/dev/null
  run "$REMOTE/migrate-service-dbs.sh" split
  [ "$status" -eq 0 ]
  [ "$(counts)" = "0 2 3 1" ]
}

@test "las dos bases con documentos: falla sin mover nada y nombra solo las colecciones" {
  root 'db.getSiblingDB("vito_chat").chat_messages.insertOne({room: 9})' >/dev/null
  run "$REMOTE/migrate-service-dbs.sh" split
  [ "$status" -eq 1 ]
  [[ "$output" == *"vito_db.chat_messages y vito_chat.chat_messages"* ]]
  # media tampoco se movió: el plan se revisa entero antes de mover
  run root 'print(db.getSiblingDB("vito_db").media.countDocuments())'
  [ "$output" = 3 ]
}

@test "sin root en el .env sale con 2" {
  : >"$VITO_ROOT/.env"
  run "$REMOTE/migrate-service-dbs.sh" split
  [ "$status" -eq 2 ]
  [[ "$output" == *MONGO_ROOT_USERNAME* ]]
}

@test "argumento inválido sale con 2" {
  run "$REMOTE/migrate-service-dbs.sh" otro
  [ "$status" -eq 2 ]
}
