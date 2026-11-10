#!/usr/bin/env bash
# Crea o actualiza los usuarios de Mongo por servicio (T-I19, M12; Principio II):
# vito_<svc> con readWrite solo en la base del servicio. La lista y la lógica están en
# service-users.js, que también corre en el initdb de mongodb para un volumen vacío.
# Idempotente. Usa root y MONGO_<SVC>_PASSWORD del .env; ningún secreto va en la línea de
# comandos ni a la salida.
#
#   ensure-service-users.sh
#
# Salida: 0 listo (u omitido si mongodb no corre) · 2 falta una contraseña en el .env · 1 falló
set -euo pipefail

VITO_ROOT=${VITO_ROOT:-/opt/vito}
ENV_FILE=${DEPLOY_ENV_FILE:-$VITO_ROOT/.env}
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SERVICES=(AUTH SOCIAL EVENT CHECKIN MOSAIC STREAMING ARCHIVE CHAT MEDIA)

mongo_container() {
  docker ps -q --filter label=com.docker.compose.project=vito \
    --filter label=com.docker.compose.service=mongodb --filter status=running | head -n1
}

# env_value <clave>: valor en el .env, sin comillas envolventes
env_value() {
  local v
  v=$(sed -n "s/^$1=//p" "$ENV_FILE" | tail -n1)
  v=${v%\"}
  v=${v#\"}
  v=${v%\'}
  v=${v#\'}
  printf '%s' "$v"
}

vars=(MONGO_ROOT_USERNAME MONGO_ROOT_PASSWORD)
missing=()
for s in "${SERVICES[@]}"; do
  vars+=("MONGO_${s}_PASSWORD")
done
for v in "${vars[@]}"; do
  val=$(env_value "$v")
  [[ -n "$val" ]] || missing+=("$v")
  printf -v "$v" '%s' "$val"
  export "${v?}"
done
if ((${#missing[@]})); then
  echo "ensure-service-users: faltan en el .env: ${missing[*]}" >&2
  exit 2
fi

cid=$(mongo_container)
if [[ -z "$cid" ]]; then
  # Volumen nuevo: los crea el initdb de mongodb al arrancar (service-users.js)
  echo "ensure-service-users: mongodb no corre (primer despliegue); se omite" >&2
  exit 0
fi

env_args=()
for v in "${vars[@]}"; do
  env_args+=(-e "$v")
done

if ! out=$(docker exec "${env_args[@]}" "$cid" \
  mongosh --nodb --quiet --eval "$(cat "$HERE/service-users.js")" 2>&1); then
  # El error de service-users.js solo lleva nombres de variables, nunca valores
  echo "ensure-service-users: no se pudieron crear o actualizar los usuarios" >&2
  grep -o 'faltan contraseñas: .*\|contraseñas con caracteres .*' <<<"$out" >&2 || true
  exit 1
fi
echo "ensure-service-users: ${#SERVICES[@]} usuarios listos" >&2
