#!/usr/bin/env bash
# Cabeceras de seguridad de api. y live. (T-I18, T051 A4) con el Caddyfile real.
#
# Levanta la imagen de Caddy de docker-compose.prod.yml con DOMAIN=localhost (TLS con la CA
# interna de Caddy) y backends falsos con los alias de Compose (api-gateway, srs) que mandan
# su propio Server. Comprueba con curl que cada respuesta, proxied o generada por Caddy,
# trae HSTS, nosniff y Referrer-Policy, y ninguna cabecera Server.
#
# Uso: infrastructure/caddy/tests/security-headers.sh   (requiere Docker)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
IMAGE="$(sed -n 's/^ *image: \(caddy:[^ ]*\).*/\1/p' "$ROOT/docker-compose.prod.yml" | head -n 1)"
NAME="vito-caddy-test-$$"

# Git Bash en Windows: que no reescriba las rutas de los -v
export MSYS_NO_PATHCONV=1

cleanup() {
  docker rm -f "$NAME-caddy" "$NAME-gateway" "$NAME-srs" >/dev/null 2>&1 || true
  docker network rm "$NAME" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "$NAME" >/dev/null

docker run -d --name "$NAME-gateway" --network "$NAME" --network-alias api-gateway "$IMAGE" \
  caddy respond --listen :8080 --header "Server: upstream-gateway" gateway >/dev/null

# srs: señalización WebRTC en 1985 y reproducción en 8080, como en el Caddyfile
docker run -d --name "$NAME-srs" --network "$NAME" --network-alias srs --entrypoint sh "$IMAGE" -c \
  'caddy respond --listen :1985 --header "Server: SRS/6" rtc & exec caddy respond --listen :8080 --header "Server: SRS/6" playback' \
  >/dev/null

docker run -d --name "$NAME-caddy" --network "$NAME" -p 127.0.0.1::443 \
  -e DOMAIN=localhost -e ACME_EMAIL=ci@example.com \
  -v "$ROOT/infrastructure/caddy/Caddyfile:/etc/caddy/Caddyfile:ro" "$IMAGE" >/dev/null

PORT="$(docker port "$NAME-caddy" 443/tcp | head -n 1 | sed 's/.*://')"

# headers <host> <path>: cabeceras de la respuesta, sin \r
headers() {
  curl -ksS -o /dev/null -D - --resolve "$1:$PORT:127.0.0.1" "https://$1:$PORT$2" | tr -d '\r'
}

for _ in $(seq 1 30); do
  headers api.localhost / >/dev/null 2>&1 && break
  sleep 1
done

failures=0

# check <host> <path> <status esperado>
check() {
  local host=$1 path=$2 status=$3 response
  response="$(headers "$host" "$path")"
  echo "$host$path → $(head -n 1 <<<"$response")"

  grep -q "^HTTP/[0-9.]* $status" <<<"$response" || { echo "  ✗ se esperaba $status"; failures=$((failures + 1)); }
  for expected in \
    "strict-transport-security: max-age=31536000; includeSubDomains" \
    "x-content-type-options: nosniff" \
    "referrer-policy: no-referrer"; do
    grep -qixF "$expected" <<<"$response" || { echo "  ✗ falta '$expected'"; failures=$((failures + 1)); }
  done
  if grep -qi '^server:' <<<"$response"; then
    echo "  ✗ sobra $(grep -i '^server:' <<<"$response")"
    failures=$((failures + 1))
  fi
}

check api.localhost / 200                  # proxied al gateway
check api.localhost /actuator/health 404   # respond de Caddy
check live.localhost /rtc/v1/whip/ 200     # proxied a srs:1985
check live.localhost /live/evt_1.m3u8 200  # proxied a srs:8080
check live.localhost /api/v1/clients 404   # API de gestión de SRS, cerrada

if [ "$failures" -gt 0 ]; then
  echo "$failures comprobaciones fallaron"
  docker logs "$NAME-caddy" 2>&1 | tail -n 20
  exit 1
fi
echo "Cabeceras de seguridad correctas en api. y live."
