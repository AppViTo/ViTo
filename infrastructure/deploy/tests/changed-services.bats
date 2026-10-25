#!/usr/bin/env bats
# Tests de infrastructure/deploy/changed-services.sh (T009): tabla de research R2.

load test_helper

SCRIPT="$BATS_TEST_DIRNAME/../changed-services.sh"
ALL="${SERVICES[*]}"
JAVA="api-gateway auth-service social-service event-service checkin-service mosaic-service streaming-service archive-service chat-service media-service"

setup() {
  REPO="$BATS_TEST_TMPDIR/repo"
  mkdir -p "$REPO"
  cd "$REPO"
  git init -q -b main
  git config user.email test@example.test
  git config user.name test
  for f in backend/auth-service/pom.xml backend/event-service/pom.xml backend/Dockerfile \
    ai-services/transcode-service/app.py infrastructure/backup/backup.sh docs/x.md \
    docker-compose.prod.yml VERSION; do
    mkdir -p "$(dirname "$f")"
    echo v1 >"$f"
  done
  git add -A
  git commit -qm base
  BASE=$(git rev-parse HEAD)
}

# change <archivo>…: commit que modifica (o crea) cada archivo; deja HEAD en $HEAD
change() {
  local f
  for f in "$@"; do
    mkdir -p "$(dirname "$f")"
    echo "v$RANDOM" >>"$f"
  done
  git add -A
  git commit -qm change
  HEAD=$(git rev-parse HEAD)
}

@test "backend/<svc>/** → <svc>" {
  change backend/auth-service/src/Main.java
  run "$SCRIPT" "$BASE" "$HEAD"
  [ "$status" -eq 0 ]
  [ "${lines[0]}" = "services=auth-service" ]
  [ "${lines[1]}" = "bundle_changed=false" ]
}

@test "backend/Dockerfile → los 10 Java" {
  change backend/Dockerfile
  run "$SCRIPT" "$BASE" "$HEAD"
  [ "${lines[0]}" = "services=$JAVA" ]
}

@test "backend/Dockerfile.dockerignore, backend/pom.xml y backend/shared-*/** → los 10 Java" {
  local f
  for f in backend/Dockerfile.dockerignore backend/pom.xml backend/shared-web/src/A.java; do
    change "$f"
    run "$SCRIPT" "$BASE" "$HEAD"
    [ "${lines[0]}" = "services=$JAVA" ]
    git reset -q --hard "$BASE"
  done
}

@test "ai-services/<svc>/** → <svc>" {
  change ai-services/transcode-service/app.py ai-services/moderation-service/app/main.py
  run "$SCRIPT" "$BASE" "$HEAD"
  [ "${lines[0]}" = "services=transcode-service moderation-service" ]
}

@test "infrastructure/backup/** → mongo-backup" {
  change infrastructure/backup/backup.sh
  run "$SCRIPT" "$BASE" "$HEAD"
  [ "${lines[0]}" = "services=mongo-backup" ]
}

@test "compose, caddy, srs, observability, remote y VERSION → vacío más bundle_changed=true" {
  local f
  for f in docker-compose.prod.yml infrastructure/caddy/Caddyfile infrastructure/docker/srs/srs.conf \
    infrastructure/observability/config.alloy infrastructure/deploy/remote/deploy.sh VERSION; do
    change "$f"
    run "$SCRIPT" "$BASE" "$HEAD"
    [ "${lines[0]}" = "services=" ]
    [ "${lines[1]}" = "bundle_changed=true" ]
    git reset -q --hard "$BASE"
  done
}

@test "solo docs/**, specs/** y frontend/** → vacío y sin bundle" {
  change docs/y.md specs/001/spec.md frontend/vito_app/lib/main.dart
  run "$SCRIPT" "$BASE" "$HEAD"
  [ "${lines[0]}" = "services=" ]
  [ "${lines[1]}" = "bundle_changed=false" ]
}

@test "rutas que no despliegan nada: integration-tests, recommendation, vps, scripts de CI" {
  change backend/integration-tests/x.java ai-services/recommendation-service/a.py \
    infrastructure/vps/bootstrap.sh .github/workflows/ci.yml
  run "$SCRIPT" "$BASE" "$HEAD"
  [ "${lines[0]}" = "services=" ]
  [ "${lines[1]}" = "bundle_changed=false" ]
}

@test "varios cambios: sin duplicados y en el orden canónico" {
  change backend/event-service/pom.xml backend/auth-service/pom.xml infrastructure/backup/backup.sh docker-compose.prod.yml
  run "$SCRIPT" "$BASE" "$HEAD"
  [ "${lines[0]}" = "services=auth-service event-service mongo-backup" ]
  [ "${lines[1]}" = "bundle_changed=true" ]
}

@test "sin SHA previo (-) → todos y bundle" {
  run "$SCRIPT" - "$BASE"
  [ "$status" -eq 0 ]
  [ "${lines[0]}" = "services=$ALL" ]
  [ "${lines[1]}" = "bundle_changed=true" ]
}

@test "SHA previo que no es ancestro → todos" {
  git checkout -q --orphan otra
  echo x >otra.txt
  git add -A
  git commit -qm otra
  local other
  other=$(git rev-parse HEAD)
  git checkout -q main
  run "$SCRIPT" "$other" "$BASE"
  [ "${lines[0]}" = "services=$ALL" ]
}

@test "SHA previo que no existe en el repo → todos" {
  run "$SCRIPT" "$(sha f)" "$BASE"
  [ "$status" -eq 0 ]
  [ "${lines[0]}" = "services=$ALL" ]
}

@test "sin argumentos sale con 2" {
  run "$SCRIPT"
  [ "$status" -eq 2 ]
}
