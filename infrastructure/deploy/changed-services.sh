#!/usr/bin/env bash
# Qué servicios hay que reconstruir entre el último despliegue exitoso y <sha>
# (feature 001, T010, research R2). Corre en el runner, dentro del checkout.
#
#   changed-services.sh <prev_sha|-> <sha>   # "-" = no hay despliegue previo
#
# Imprime, para $GITHUB_OUTPUT:
#   services=<servicios separados por espacio, en orden canónico>
#   bundle_changed=<true|false>   # hay que sincronizar el bundle aunque no haya imágenes
set -euo pipefail

JAVA=(api-gateway auth-service social-service event-service checkin-service mosaic-service
  streaming-service archive-service chat-service media-service)
PYTHON=(transcode-service moderation-service)
SERVICES=("${JAVA[@]}" "${PYTHON[@]}" mongo-backup)

(($# == 2)) || { sed -n '5p' "$0" | sed 's/^# \{0,3\}//' >&2; exit 2; }
prev=$1
sha=$2

declare -A hit=()
bundle_changed=false

emit() {
  local out=() svc
  for svc in "${SERVICES[@]}"; do [[ -n "${hit[$svc]:-}" ]] && out+=("$svc"); done
  printf 'services=%s\n' "${out[*]}"
  printf 'bundle_changed=%s\n' "$bundle_changed"
}

mark_all() {
  local svc
  for svc in "${SERVICES[@]}"; do hit[$svc]=1; done
  bundle_changed=true
}

# Primer despliegue, SHA desconocido o historia reescrita: se reconstruye todo
if [[ "$prev" == - ]] || ! git cat-file -e "${prev}^{commit}" 2>/dev/null \
  || ! git merge-base --is-ancestor "$prev" "$sha"; then
  mark_all
  emit
  exit 0
fi

is_one_of() {
  local needle=$1 s
  shift
  for s in "$@"; do [[ "$s" == "$needle" ]] && return 0; done
  return 1
}

while IFS= read -r path; do
  case "$path" in
    backend/Dockerfile | backend/Dockerfile.dockerignore | backend/pom.xml | backend/shared-*/*)
      for svc in "${JAVA[@]}"; do hit[$svc]=1; done
      ;;
    backend/*/*)
      svc=${path#backend/}
      svc=${svc%%/*}
      if is_one_of "$svc" "${JAVA[@]}"; then hit[$svc]=1; fi
      ;;
    ai-services/*/*)
      svc=${path#ai-services/}
      svc=${svc%%/*}
      if is_one_of "$svc" "${PYTHON[@]}"; then hit[$svc]=1; fi
      ;;
    infrastructure/backup/*)
      hit[mongo-backup]=1
      ;;
    # Viajan en el bundle: Compose recrea lo que cambió de definición (R2, R4). VERSION
    # entra en versions.env (RELEASE_VERSION) y en el tag v<VERSION> del release.
    docker-compose.prod.yml | VERSION | infrastructure/caddy/* | infrastructure/docker/srs/* \
      | infrastructure/observability/* | infrastructure/deploy/remote/*)
      bundle_changed=true
      ;;
  esac
done < <(git diff --name-only "$prev" "$sha")

emit
