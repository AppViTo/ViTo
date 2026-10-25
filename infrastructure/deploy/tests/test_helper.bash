# Utilidades comunes de los tests de infrastructure/deploy (bats).

SERVICES=(api-gateway auth-service social-service event-service checkin-service
  mosaic-service streaming-service archive-service chat-service media-service
  transcode-service moderation-service mongo-backup)

DEPLOY="$BATS_TEST_DIRNAME/../remote/deploy.sh"

# SHA ficticio de 40 caracteres hexadecimales: sha a → aaaa…a
sha() { printf "%0.s$1" {1..40}; }

setup_vito() {
  export VITO_ROOT="$BATS_TEST_TMPDIR/vito"
  mkdir -p "$VITO_ROOT/releases"
  : >"$VITO_ROOT/.env"
  export FAKE_DIR="$BATS_TEST_TMPDIR/fake"
  mkdir -p "$FAKE_DIR/bin"
  cp "$BATS_TEST_DIRNAME/fake-docker" "$FAKE_DIR/bin/docker"
  chmod +x "$FAKE_DIR/bin/docker"
  export PATH="$FAKE_DIR/bin:$PATH"
  export DOCKER_LOG="$FAKE_DIR/calls.log"
  : >"$DOCKER_LOG"
  : >"$FAKE_DIR/images"
}

# make_bundle <sha> [contenido del Caddyfile]: lo que cd.yml copia a releases/<sha>/
make_bundle() {
  local dir="$VITO_ROOT/releases/$1"
  mkdir -p "$dir/infrastructure/caddy" "$dir/infrastructure/docker/srs"
  printf '%s\n' "${2:-caddy por defecto}" >"$dir/infrastructure/caddy/Caddyfile"
  echo 'listen 1935;' >"$dir/infrastructure/docker/srs/srs.conf"
  echo 'services: {}' >"$dir/docker-compose.prod.yml"
  echo '0.1.0' >"$dir/VERSION"
}

# load_images <tag> [servicio…]: simula `docker load` (todos los servicios por defecto)
load_images() {
  local tag=$1 svc
  shift
  (($#)) || set -- "${SERVICES[@]}"
  for svc in "$@"; do echo "vito/$svc:$tag" >>"$FAKE_DIR/images"; done
}

# deploy_ok <sha> [args…]: bundle + imágenes + apply, y exige éxito
deploy_ok() {
  local s=$1
  shift
  make_bundle "$s"
  load_images "$s"
  run "$DEPLOY" apply "$s" "$@"
  # shellcheck disable=SC2154 # status y output los define `run` de bats
  [ "$status" -eq 0 ] || { echo "$output"; return 1; }
}

current_sha() { basename "$(readlink "$VITO_ROOT/current")"; }

versions_of() { cat "$VITO_ROOT/releases/$1/versions.env"; }
