#!/usr/bin/env bats
# Tests de remote/live-check.sh y remote/ensure-ro-user.sh (T014, research R6) con un
# docker falso: `docker exec … mongosh` devuelve $FAKE_MONGOSH_OUT. La consulta real
# (live-check.js) se prueba contra un Mongo de verdad en live-check-mongo.bats.

load test_helper

LIVE="$BATS_TEST_DIRNAME/../remote/live-check.sh"
ENSURE="$BATS_TEST_DIRNAME/../remote/ensure-ro-user.sh"
EMPTY='{"events":[],"streams":[]}'

setup() {
  setup_vito
  # Hay un despliegue activo y mongodb corre, salvo que el test diga otra cosa
  make_bundle "$(sha a)"
  ln -s "releases/$(sha a)" "$VITO_ROOT/current"
  export FAKE_MONGO_RUNNING=1
  export MONGO_DEPLOY_RO_PASSWORD=ro-fixture
}

@test "sin eventos ni streams: exit 0" {
  export FAKE_MONGOSH_OUT=$EMPTY
  run "$LIVE"
  [ "$status" -eq 0 ]
  [ "$output" = "$EMPTY" ]
}

@test "evento en curso: exit 3 con id y título" {
  export FAKE_MONGOSH_OUT='{"events":[{"id":"e1","title":"Boda"}],"streams":[]}'
  run "$LIVE"
  [ "$status" -eq 3 ]
  [[ "$output" == *'"id": "e1"'* || "$output" == *'"id":"e1"'* ]]
  [[ "$output" == *Boda* ]]
}

@test "stream activo: exit 3" {
  export FAKE_MONGOSH_OUT='{"events":[],"streams":[{"id":"s1","eventId":"e1"}]}'
  run "$LIVE"
  [ "$status" -eq 3 ]
}

@test "mongosh falla: exit 7" {
  export FAKE_MONGOSH_RC=1
  run "$LIVE"
  [ "$status" -eq 7 ]
}

@test "salida que no es el JSON esperado: exit 7" {
  export FAKE_MONGOSH_OUT='MongoServerError: Authentication failed'
  run "$LIVE"
  [ "$status" -eq 7 ]
}

@test "sin MONGO_DEPLOY_RO_PASSWORD: exit 7 sin consultar" {
  unset MONGO_DEPLOY_RO_PASSWORD
  run "$LIVE"
  [ "$status" -eq 7 ]
  ! grep -q '^exec' "$DOCKER_LOG"
}

@test "sin current (primer despliegue): exit 0, skipped y sin llamar a mongosh" {
  rm "$VITO_ROOT/current"
  run "$LIVE"
  [ "$status" -eq 0 ]
  [ "$output" = '{"skipped":"first-deploy"}' ]
  ! grep -q '^exec' "$DOCKER_LOG"
}

@test "mongodb detenido: exit 0 y skipped" {
  export FAKE_MONGO_RUNNING=0
  run "$LIVE"
  [ "$status" -eq 0 ]
  [ "$output" = '{"skipped":"first-deploy"}' ]
  ! grep -q '^exec' "$DOCKER_LOG"
}

@test "la contraseña va por variable de entorno, nunca en los argumentos" {
  export FAKE_MONGOSH_OUT=$EMPTY
  run "$LIVE"
  [ "$status" -eq 0 ]
  ! grep -q ro-fixture "$DOCKER_LOG"
  grep -qx 'MONGO_DEPLOY_RO_PASSWORD=ro-fixture' "$FAKE_DIR/exec-env"
}

@test "ensure-ro-user: pasa las credenciales por entorno y no las imprime" {
  printf 'MONGO_ROOT_USERNAME=vito_admin\nMONGO_ROOT_PASSWORD="root-fixture"\n' >"$VITO_ROOT/.env"
  export FAKE_MONGOSH_OUT=ok
  run "$ENSURE"
  [ "$status" -eq 0 ]
  [[ "$output" != *fixture* ]]
  ! grep -qE 'ro-fixture|root-fixture' "$DOCKER_LOG"
  grep -qx 'MONGO_ROOT_USERNAME=vito_admin' "$FAKE_DIR/exec-env"
  grep -qx 'MONGO_ROOT_PASSWORD=root-fixture' "$FAKE_DIR/exec-env"
  grep -qx 'MONGO_DEPLOY_RO_PASSWORD=ro-fixture' "$FAKE_DIR/exec-env"
}

@test "ensure-ro-user: con mongodb detenido se omite (exit 0)" {
  export FAKE_MONGO_RUNNING=0
  run "$ENSURE"
  [ "$status" -eq 0 ]
  ! grep -q '^exec' "$DOCKER_LOG"
}

@test "ensure-ro-user: sin la contraseña del usuario de solo lectura sale con 2" {
  unset MONGO_DEPLOY_RO_PASSWORD
  run "$ENSURE"
  [ "$status" -eq 2 ]
}

@test "ensure-ro-user: si mongosh falla, falla" {
  printf 'MONGO_ROOT_USERNAME=vito_admin\nMONGO_ROOT_PASSWORD=x\n' >"$VITO_ROOT/.env"
  export FAKE_MONGOSH_RC=1
  run "$ENSURE"
  [ "$status" -ne 0 ]
}
