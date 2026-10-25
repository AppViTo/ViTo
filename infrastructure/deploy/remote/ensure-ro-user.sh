#!/usr/bin/env bash
# Crea o actualiza el usuario de solo lectura vito_deploy_ro que usa live-check.sh
# (feature 001, T015, research R6): rol `read` en vito_events y vito_streaming, nada más.
# Idempotente. Usa las credenciales root del .env; ningún secreto va en la línea de
# comandos ni a la salida.
#
#   MONGO_DEPLOY_RO_PASSWORD=… ensure-ro-user.sh
#
# Salida: 0 listo (u omitido si mongodb no corre) · 2 falta la contraseña · 1 falló
set -euo pipefail

VITO_ROOT=${VITO_ROOT:-/opt/vito}
ENV_FILE=${DEPLOY_ENV_FILE:-$VITO_ROOT/.env}

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

if [[ -z "${MONGO_DEPLOY_RO_PASSWORD:-}" ]]; then
  echo "ensure-ro-user: falta MONGO_DEPLOY_RO_PASSWORD" >&2
  exit 2
fi

cid=$(mongo_container)
if [[ -z "$cid" ]]; then
  echo "ensure-ro-user: mongodb no corre (primer despliegue); se omite" >&2
  exit 0
fi

MONGO_ROOT_USERNAME=$(env_value MONGO_ROOT_USERNAME)
MONGO_ROOT_PASSWORD=$(env_value MONGO_ROOT_PASSWORD)
export MONGO_ROOT_USERNAME MONGO_ROOT_PASSWORD MONGO_DEPLOY_RO_PASSWORD

read -r -d '' JS <<'EOF' || true
const e = encodeURIComponent;
const env = process.env;
const conn = new Mongo(`mongodb://${e(env.MONGO_ROOT_USERNAME)}:${e(env.MONGO_ROOT_PASSWORD)}@localhost:27017/?authSource=admin`);
const admin = conn.getDB('admin');
const roles = [{ role: 'read', db: 'vito_events' }, { role: 'read', db: 'vito_streaming' }];
if (admin.getUser('vito_deploy_ro')) {
  admin.updateUser('vito_deploy_ro', { pwd: env.MONGO_DEPLOY_RO_PASSWORD, roles });
} else {
  admin.createUser({ user: 'vito_deploy_ro', pwd: env.MONGO_DEPLOY_RO_PASSWORD, roles });
}
print('ok');
EOF

if ! docker exec -e MONGO_ROOT_USERNAME -e MONGO_ROOT_PASSWORD -e MONGO_DEPLOY_RO_PASSWORD "$cid" \
  mongosh --nodb --quiet --eval "$JS" >/dev/null 2>&1; then
  echo "ensure-ro-user: no se pudo crear o actualizar vito_deploy_ro" >&2
  exit 1
fi
echo "ensure-ro-user: vito_deploy_ro listo" >&2
