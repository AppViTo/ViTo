#!/usr/bin/env bats
# Tests de .github/scripts/ci-changes.sh (T-I12): qué partes de ci.yml corren en un PR según
# los archivos que cambió.

SCRIPT="$BATS_TEST_DIRNAME/../../../.github/scripts/ci-changes.sh"
JAVA_ALL='["api-gateway","archive-service","auth-service","chat-service","checkin-service","event-service","media-service","mosaic-service","social-service","streaming-service"]'

changes() {
  run bash "$SCRIPT" < <(printf '%s\n' "$@")
  [ "$status" -eq 0 ]
}

out() { grep "^$1=" <<<"$output" | cut -d= -f2-; }

@test "solo frontend: Flutter sí, nada de backend, Python, imágenes ni Prod Startup" {
  changes frontend/vito_app/lib/main.dart
  [ "$(out flutter)" = true ]
  [ "$(out java)" = '[]' ]
  [ "$(out python)" = '[]' ]
  [ "$(out images)" = '[]' ]
  [ "$(out prod)" = false ]
  [ "$(out deploy)" = false ]
}

@test "un servicio Java: solo sus pruebas, las 10 imágenes Java y Prod Startup; sin Flutter" {
  changes backend/auth-service/src/main/java/X.java
  [ "$(out java)" = '["auth-service"]' ]
  [ "$(out images)" = "$JAVA_ALL" ]
  [ "$(out prod)" = true ]
  [ "$(out flutter)" = false ]
  [ "$(out python)" = '[]' ]
}

@test "reactor, shared-web, wrapper o Dockerfile común: los 10 servicios Java" {
  for f in backend/pom.xml backend/shared-web/src/X.java backend/.mvn/wrapper/maven-wrapper.properties \
    backend/mvnw backend/Dockerfile backend/Dockerfile.dockerignore; do
    changes "$f"
    [ "$(out java)" = "$JAVA_ALL" ] || { echo "$f -> $(out java)"; return 1; }
  done
}

@test "carpeta de backend que no es un servicio (p. ej. backend/out): nada" {
  changes backend/out/x.txt backend/README.md
  [ "$(out java)" = '[]' ]
  [ "$(out prod)" = false ]
}

@test "contrato y fixtures: todos los Java y Flutter (sus pruebas los leen)" {
  changes docs/api/fixtures/authentication/login.200.json
  [ "$(out java)" = "$JAVA_ALL" ]
  [ "$(out flutter)" = true ]
}

@test "docs fuera del contrato: nada pesado" {
  changes docs/roadmap/tasks.md README.md
  [ "$(out java)" = '[]' ]
  [ "$(out flutter)" = false ]
  [ "$(out images)" = '[]' ]
  [ "$(out deploy)" = false ]
}

@test "Python: su prueba y su imagen, sin Prod Startup" {
  changes ai-services/moderation-service/app/main.py
  [ "$(out python)" = '["moderation-service"]' ]
  [ "$(out images)" = '["moderation-service"]' ]
  [ "$(out prod)" = false ]
}

@test "infraestructura: Deploy Scripts; backup además construye su imagen" {
  changes infrastructure/deploy/remote/live-check.sh
  [ "$(out deploy)" = true ]
  [ "$(out images)" = '[]' ]
  changes infrastructure/backup/backup.sh
  [ "$(out deploy)" = true ]
  [ "$(out images)" = '["mongo-backup"]' ]
}

@test "docker-compose.prod.yml: todas las imágenes, Prod Startup y Deploy Scripts" {
  changes docker-compose.prod.yml
  [ "$(out prod)" = true ]
  [ "$(out deploy)" = true ]
  [[ "$(out images)" == *'"mongo-backup"'* ]]
  [[ "$(out images)" == *'"transcode-service"'* ]]
  [[ "$(out images)" == *'"api-gateway"'* ]]
  [ "$(out java)" = '[]' ]
}

@test "el propio CI o este script: todo" {
  for f in .github/workflows/ci.yml .github/scripts/ci-changes.sh; do
    changes "$f"
    [ "$(out java)" = "$JAVA_ALL" ]
    [ "$(out flutter)" = true ]
    [ "$(out deploy)" = true ]
    [ "$(out python)" = '["moderation-service","transcode-service"]' ]
  done
}

@test "otro workflow: solo Deploy Scripts (actionlint)" {
  changes .github/workflows/cd.yml
  [ "$(out deploy)" = true ]
  [ "$(out java)" = '[]' ]
  [ "$(out flutter)" = false ]
}

@test "FORCE_ALL=1 (push, semanal, manual) sin archivos: todo" {
  FORCE_ALL=1 run bash "$SCRIPT" < /dev/null
  [ "$status" -eq 0 ]
  [ "$(out java)" = "$JAVA_ALL" ]
  [ "$(out prod)" = true ]
  [ "$(out flutter)" = true ]
  [ "$(out deploy)" = true ]
  [ "$(out python)" = '["moderation-service","transcode-service"]' ]
  [[ "$(out images)" == *'"mongo-backup"'* ]]
}

@test "renombrado (dos rutas) y fin de línea CRLF" {
  run bash "$SCRIPT" < <(printf 'frontend/a.dart\r\nbackend/chat-service/pom.xml\r\n')
  [ "$status" -eq 0 ]
  [ "$(out java)" = '["chat-service"]' ]
  [ "$(out flutter)" = true ]
}
