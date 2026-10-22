#!/usr/bin/env bash
# Despliegue en el VPS (feature 001, T008). Viaja en el bundle y corre como `deploy`.
# Contrato: specs/001-cd-produccion/contracts/remote-scripts.md
#
#   deploy.sh apply    <sha> [--changed "svc1 svc2"] [--meta <json>]
#   deploy.sh rollback <sha> [--meta <json>]
#   deploy.sh check    <sha>
#   deploy.sh status
#
# Salida: 0 succeeded · 2 configuración, secreto o argumento inválido (no se tocó nada) ·
# 4 imagen no cargada, bundle inexistente o rollback a un SHA no retenido (no se tocó nada) ·
# 5 salud falló y se volvió al anterior · 6 salud falló sin anterior o la vuelta falló
#
# Nunca imprime valores del .env: compose solo recibe la ruta.
set -euo pipefail

VITO_ROOT=${VITO_ROOT:-/opt/vito}
RELEASES=$VITO_ROOT/releases
CURRENT=$VITO_ROOT/current
ENV_FILE=$VITO_ROOT/.env
HISTORY=$VITO_ROOT/history
LOG=$VITO_ROOT/deployments.log
KEEP=3
WAIT_TIMEOUT=${DEPLOY_WAIT_TIMEOUT:-300}

# Servicios desplegables (data-model.md): uno por TAG_* del compose
SERVICES=(api-gateway auth-service social-service event-service checkin-service
  mosaic-service streaming-service archive-service chat-service media-service
  transcode-service moderation-service mongo-backup)

# Estado del registro (deployments.log)
L_TYPE='' L_SHA='' L_FROM='' L_CHANGED='' L_META='{}' L_VERSIONS='' L_AT='' L_START=0

log() { printf '==> %s\n' "$*" >&2; }

usage() {
  sed -n '5,8p' "$0" | sed 's/^# \{0,3\}//' >&2
  exit 2
}

tag_var() {
  local v=${1^^}
  printf 'TAG_%s' "${v//-/_}"
}

is_service() {
  local s
  for s in "${SERVICES[@]}"; do [[ "$s" == "$1" ]] && return 0; done
  return 1
}

current_sha() {
  if [[ -L "$CURRENT" ]]; then basename "$(readlink "$CURRENT")"; fi
}

file_hash() {
  if [[ -f "$1" ]]; then sha256sum "$1" | cut -d' ' -f1; else echo none; fi
}

# compose <bundle> <versions.env> args… — siempre el mismo proyecto y directorio (R4): los
# montajes salen de current/ aunque se valide otro bundle. `config` no exige que exista.
compose() {
  local bundle=$1 versions=$2
  shift 2
  docker compose -p vito --project-directory "$CURRENT" -f "$bundle/docker-compose.prod.yml" \
    --env-file "$ENV_FILE" --env-file "$versions" "$@"
}

# generate_versions <bundle> <salida> [servicio…]: manifiesto del activo + los cambiados (R1)
generate_versions() {
  local bundle=$1 out=$2 sha prev='' svc var tag
  shift 2
  sha=$(basename "$bundle")
  [[ -f "$CURRENT/versions.env" ]] && prev=$CURRENT/versions.env
  {
    for svc in "${SERVICES[@]}"; do
      var=$(tag_var "$svc")
      tag=''
      [[ -n "$prev" ]] && tag=$(sed -n "s/^${var}=//p" "$prev" | tail -n1)
      # Primer despliegue, o un servicio que el activo no tenía: va con este SHA
      [[ -z "$tag" ]] && tag=$sha
      [[ " $* " == *" $svc "* ]] && tag=$sha
      printf '%s=%s\n' "$var" "$tag"
    done
    printf 'DEPLOYED_SHA=%s\n' "$sha"
    printf 'RELEASE_VERSION=%s\n' "$(tr -d '[:space:]' <"$bundle/VERSION" 2>/dev/null || echo unknown)"
    printf 'CFG_CADDY=%s\n' "$(file_hash "$bundle/infrastructure/caddy/Caddyfile")"
    printf 'CFG_SRS=%s\n' "$(file_hash "$bundle/infrastructure/docker/srs/srs.conf")"
    printf 'CFG_ALLOY=%s\n' "$(file_hash "$bundle/infrastructure/observability/config.alloy")"
  } >"$out"
}

# missing_images <versions.env>: imprime las imágenes que no están cargadas
missing_images() {
  local svc tag ref missing=0
  for svc in "${SERVICES[@]}"; do
    tag=$(sed -n "s/^$(tag_var "$svc")=//p" "$1" | tail -n1)
    ref="vito/$svc:$tag"
    if ! docker image inspect "$ref" >/dev/null 2>&1; then
      printf '  falta %s\n' "$ref" >&2
      missing=1
    fi
  done
  return "$missing"
}

activate() {
  ln -sfn "releases/$1" "$VITO_ROOT/current.new"
  mv -Tf "$VITO_ROOT/current.new" "$CURRENT"
}

up() {
  compose "$CURRENT" "$CURRENT/versions.env" up -d --wait --wait-timeout "$WAIT_TIMEOUT"
}

record_success() {
  local tmp
  tmp=$(mktemp "$VITO_ROOT/.history.XXXXXX")
  { grep -vxF "$1" "$HISTORY" 2>/dev/null || true; echo "$1"; } >"$tmp"
  mv "$tmp" "$HISTORY"
}

retained() { tail -n "$KEEP" "$HISTORY" 2>/dev/null || true; }

is_retained() { retained | grep -qxF "$1"; }

# Borra los bundles fuera de los últimos $KEEP con éxito (y del activo) y las imágenes
# propias que ninguno de ellos usa. Solo toca tags con forma de SHA.
prune() {
  local keep=() dir s ref
  mapfile -t keep < <(retained)
  keep+=("$(current_sha)")
  for dir in "$RELEASES"/*/; do
    s=$(basename "$dir")
    printf '%s\n' "${keep[@]}" | grep -qxF "$s" || { log "poda: bundle $s"; rm -rf "${RELEASES:?}/$s"; }
  done
  local used
  used=$(for s in "${keep[@]}"; do
    [[ -f "$RELEASES/$s/versions.env" ]] || continue
    for svc in "${SERVICES[@]}"; do
      printf 'vito/%s:%s\n' "$svc" "$(sed -n "s/^$(tag_var "$svc")=//p" "$RELEASES/$s/versions.env")"
    done
  done | sort -u)
  while read -r ref; do
    [[ "$ref" =~ ^vito/[a-z-]+:[0-9a-f]{40}$ ]] || continue
    grep -qxF "$ref" <<<"$used" || { log "poda: imagen $ref"; docker image rm "$ref" >/dev/null || true; }
  done < <(docker image ls --format '{{.Repository}}:{{.Tag}}' 'vito/*')
}

# finish <código> <resultado>: escribe la línea del registro (data-model, *Despliegue*),
# la repite en stdout para el job summary y sale.
finish() {
  local code=$1 result=$2 line
  line=$(L_CODE=$code L_RESULT=$result L_DURATION=$(($(date +%s) - L_START)) \
    L_TYPE=$L_TYPE L_SHA=$L_SHA L_FROM=$L_FROM L_CHANGED=$L_CHANGED L_META=$L_META \
    L_VERSIONS=$L_VERSIONS L_AT=$L_AT python3 - <<'PY'
import json, os

env = os.environ
meta = json.loads(env["L_META"])
versions = {}
path = env["L_VERSIONS"]
if path and os.path.isfile(path):
    for raw in open(path):
        key, _, value = raw.strip().partition("=")
        if key.startswith("TAG_"):
            versions[key[4:].lower().replace("_", "-")] = value
rec = {
    "at": env["L_AT"],
    "type": env["L_TYPE"],
    "kind": meta.get("kind", "release") if env["L_TYPE"] == "deploy" else None,
    "sha": env["L_SHA"],
    "from_sha": env["L_FROM"] or None,
    "versions": versions,
    "changed": env["L_CHANGED"].split(),
    "approver": meta.get("approver"),
    "triggered_by": meta.get("triggered_by"),
    "reason": meta.get("reason"),
    "forced_live_event": bool(meta.get("forced_live_event", False)),
    "live_check": meta.get("live_check"),
    "result": env["L_RESULT"],
    "exit_code": int(env["L_CODE"]),
    "duration_s": int(env["L_DURATION"]),
    "run_url": meta.get("run_url"),
}
print(json.dumps(rec, separators=(",", ":"), ensure_ascii=False))
PY
  )
  printf '%s\n' "$line" >>"$LOG"
  printf '%s\n' "$line"
  exit "$code"
}

# recover <sha anterior>: vuelta automática después de un up fallido
recover() {
  local prev=$1
  if [[ -z "$prev" ]]; then
    log "la salud falló y no hay despliegue anterior: requiere un lead"
    finish 6 failed
  fi
  log "la salud falló: vuelvo a $prev"
  activate "$prev"
  if up; then finish 5 rolled_back; fi
  log "la vuelta a $prev también falló: requiere un lead"
  finish 6 failed
}

start_record() {
  L_TYPE=$1 L_SHA=$2 L_FROM=$(current_sha)
  L_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  L_START=$(date +%s)
}

cmd_apply() {
  local sha=$1 changed=$2 bundle=$RELEASES/$1 svc tmp
  start_record deploy "$sha"
  L_CHANGED=$changed
  for svc in $changed; do
    is_service "$svc" || { log "servicio desconocido en --changed: $svc"; finish 2 failed; }
  done
  [[ -d "$bundle" ]] || { log "no existe el bundle $bundle"; finish 4 failed; }

  tmp=$(mktemp "$bundle/.versions.XXXXXX")
  # shellcheck disable=SC2086 # la lista de servicios se separa por espacios a propósito
  generate_versions "$bundle" "$tmp" $changed
  mv "$tmp" "$bundle/versions.env"
  L_VERSIONS=$bundle/versions.env

  log "validando la configuración de $sha"
  compose "$bundle" "$bundle/versions.env" config -q || finish 2 failed
  missing_images "$bundle/versions.env" || { log "faltan imágenes"; finish 4 failed; }

  log "activando $sha"
  activate "$sha"
  if up; then
    record_success "$sha"
    prune
    finish 0 succeeded
  fi
  L_VERSIONS=$CURRENT/versions.env
  recover "$L_FROM"
}

cmd_rollback() {
  local sha=$1 bundle=$RELEASES/$1
  start_record rollback "$sha"
  if ! is_retained "$sha" || [[ ! -f "$bundle/versions.env" ]]; then
    log "$sha no es un release retenido; disponibles: $(retained | tr '\n' ' ')"
    finish 4 failed
  fi
  L_VERSIONS=$bundle/versions.env
  compose "$bundle" "$bundle/versions.env" config -q || finish 2 failed
  missing_images "$bundle/versions.env" || { log "faltan imágenes"; finish 4 failed; }

  log "volviendo a $sha"
  activate "$sha"
  if up; then
    record_success "$sha"
    prune
    finish 0 succeeded
  fi
  recover "$L_FROM"
}

# Verificación previa (R8): no cambia current, no reinicia nada y no escribe en el bundle
cmd_check() {
  local sha=$1 bundle=$RELEASES/$1 tmp rc=0
  [[ -d "$bundle" ]] || { log "no existe el bundle $bundle"; exit 4; }
  tmp=$(mktemp)
  generate_versions "$bundle" "$tmp"
  compose "$bundle" "$tmp" config -q || rc=2
  rm -f "$tmp"
  ((rc == 0)) || { log "configuración inválida o secreto faltante"; exit "$rc"; }
  log "configuración de $sha válida"
  cmd_status
}

cmd_status() {
  local cur
  cur=$(current_sha)
  if [[ -z "$cur" ]]; then
    echo "Sin despliegue activo."
    return 0
  fi
  grep -E '^(DEPLOYED_SHA|RELEASE_VERSION|TAG_[A-Z_]+)=' "$CURRENT/versions.env"
  compose "$CURRENT" "$CURRENT/versions.env" ps --format 'table {{.Service}}\t{{.Status}}'
}

main() {
  (($#)) || usage
  local cmd=$1 sha='' changed='' meta='{}'
  shift
  if [[ "$cmd" != status ]]; then
    (($#)) || usage
    sha=$1
    shift
    [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || { log "SHA inválido: $sha"; exit 2; }
  fi
  while (($#)); do
    (($# >= 2)) || usage
    case "$1:$cmd" in
      --changed:apply) changed=$2 ;;
      --meta:apply | --meta:rollback) meta=$2 ;;
      *) usage ;;
    esac
    shift 2
  done
  python3 -c 'import json, sys; assert isinstance(json.loads(sys.argv[1]), dict)' "$meta" 2>/dev/null \
    || { log "--meta debe ser un objeto JSON"; exit 2; }
  L_META=$meta

  case "$cmd" in
    apply) cmd_apply "$sha" "$changed" ;;
    rollback) cmd_rollback "$sha" ;;
    check) cmd_check "$sha" ;;
    status) cmd_status ;;
    *) usage ;;
  esac
}

main "$@"
