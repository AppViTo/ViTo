#!/usr/bin/env bash
# Pone las colecciones de chat-service y media-service en la base que espera el release que se
# va a activar (T-B40). La lógica está en service-dbs.js. Lo llama deploy.sh antes de cada `up`.
#
#   migrate-service-dbs.sh split    vito_db → vito_chat / vito_media (release con T-B40)
#   migrate-service-dbs.sh shared   de vuelta a vito_db (release anterior a T-B40)
#
# Si hay algo que mover, para chat-service y media-service antes (el `up` que sigue los
# vuelve a arrancar): así no escriben en la base vieja mientras se mueve. Usa root del .env;
# ningún secreto va en la línea de comandos ni a la salida.
#
# Salida: 0 listo, nada que mover u omitido (mongodb no corre) · 2 argumento o credencial
# faltante · 1 falló (incluye conflicto: las dos bases tienen documentos; no se movió nada)
set -euo pipefail

VITO_ROOT=${VITO_ROOT:-/opt/vito}
ENV_FILE=${DEPLOY_ENV_FILE:-$VITO_ROOT/.env}
HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

layout=${1:-}
if [[ "$layout" != split && "$layout" != shared ]]; then
  echo "uso: migrate-service-dbs.sh split|shared" >&2
  exit 2
fi

container() {
  docker ps -q --filter label=com.docker.compose.project=vito \
    --filter "label=com.docker.compose.service=$1" --filter status=running | head -n1
}

env_value() {
  local v
  v=$(sed -n "s/^$1=//p" "$ENV_FILE" | tail -n1)
  v=${v%\"}
  v=${v#\"}
  v=${v%\'}
  v=${v#\'}
  printf '%s' "$v"
}

for v in MONGO_ROOT_USERNAME MONGO_ROOT_PASSWORD; do
  val=$(env_value "$v")
  if [[ -z "$val" ]]; then
    echo "migrate-service-dbs: falta $v en el .env" >&2
    exit 2
  fi
  printf -v "$v" '%s' "$val"
  export "${v?}"
done
export LAYOUT=$layout

cid=$(container mongodb)
if [[ -z "$cid" ]]; then
  echo "migrate-service-dbs: mongodb no corre (primer despliegue); se omite" >&2
  exit 0
fi

# run_js [PLAN=1]: corre service-dbs.js; solo pasa nombres de variables a docker
run_js() {
  docker exec -e MONGO_ROOT_USERNAME -e MONGO_ROOT_PASSWORD -e LAYOUT -e PLAN "$cid" \
    mongosh --nodb --quiet --eval "$(cat "$HERE/service-dbs.js")" 2>&1
}

# Mensajes de error de service-dbs.js: solo nombres de bases y colecciones
errors() { grep -o 'conflicto.*\|renameCollection .*\|LAYOUT .*' <<<"$1" >&2 || true; }

if ! out=$(PLAN=1 run_js); then
  echo "migrate-service-dbs: no se pudo planificar ($layout)" >&2
  errors "$out"
  exit 1
fi
pending=$(sed -n 's/^pending //p' <<<"$out" | tail -n1)
if [[ "${pending:-0}" == 0 ]]; then
  echo "migrate-service-dbs: nada que mover ($layout)" >&2
  exit 0
fi

for svc in chat-service media-service; do
  c=$(container "$svc")
  if [[ -n "$c" ]]; then
    echo "migrate-service-dbs: paro $svc" >&2
    docker stop "$c" >/dev/null
  fi
done

if ! out=$(PLAN=0 run_js); then
  echo "migrate-service-dbs: la migración falló ($layout)" >&2
  errors "$out"
  exit 1
fi
grep -E '^(movida|borrada) ' <<<"$out" >&2 || true
echo "migrate-service-dbs: $pending colecciones listas ($layout)" >&2
