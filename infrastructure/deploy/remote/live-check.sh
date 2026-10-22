#!/usr/bin/env bash
# ¿Hay un evento o un stream en vivo? (feature 001, T016, research R6). Corre en el VPS
# como `deploy`, antes de tocar servicios. Contrato: contracts/remote-scripts.md
#
#   MONGO_DEPLOY_RO_PASSWORD=… live-check.sh
#
# Imprime {"events":[{"id","title"}],"streams":[{"id","eventId"}]} y sale con
# 0 libre · 3 bloqueado · 7 no se pudo comprobar (también bloquea).
# Sin despliegue activo o sin mongodb corriendo no puede haber eventos: imprime
# {"skipped":"first-deploy"} y sale con 0 sin consultar nada.
set -euo pipefail

VITO_ROOT=${VITO_ROOT:-/opt/vito}
HERE=$(cd "$(dirname "$0")" && pwd)

mongo_container() {
  docker ps -q --filter label=com.docker.compose.project=vito \
    --filter label=com.docker.compose.service=mongodb --filter status=running | head -n1
}

cid=''
[[ -e "$VITO_ROOT/current" ]] && cid=$(mongo_container)
if [[ -z "$cid" ]]; then
  echo '{"skipped":"first-deploy"}'
  exit 0
fi

if [[ -z "${MONGO_DEPLOY_RO_PASSWORD:-}" ]]; then
  echo "live-check: falta MONGO_DEPLOY_RO_PASSWORD" >&2
  exit 7
fi

# -e sin valor: docker toma la variable del entorno, no de la línea de comandos
if ! out=$(docker exec -e MONGO_DEPLOY_RO_PASSWORD "$cid" \
  mongosh --nodb --quiet --eval "$(cat "$HERE/live-check.js")" 2>&1); then
  echo "live-check: la consulta falló" >&2
  exit 7
fi

LIVE_OUT=$out python3 - <<'PY'
import json, os, sys

try:
    data = json.loads(os.environ["LIVE_OUT"].strip().splitlines()[-1])
    events, streams = data["events"], data["streams"]
    assert isinstance(events, list) and isinstance(streams, list)
except Exception:
    print("live-check: respuesta inesperada de mongosh", file=sys.stderr)
    sys.exit(7)
print(json.dumps({"events": events, "streams": streams}, separators=(",", ":"), ensure_ascii=False))
sys.exit(3 if events or streams else 0)
PY
