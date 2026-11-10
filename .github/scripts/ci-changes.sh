#!/usr/bin/env bash
# T-I12: decide qué partes de ci.yml corren según los archivos que cambió el PR.
#
# Entrada: la lista de archivos cambiados, uno por línea, por stdin.
# Salida: líneas clave=valor para $GITHUB_OUTPUT:
#   java       JSON con los servicios Java a probar          ["auth-service", ...]
#   images     JSON con las imágenes Docker a construir     ["api-gateway", ..., "mongo-backup"]
#   prod       true si corre Prod Startup (necesita las 10 imágenes Java)
#   python     JSON con los servicios Python a probar       ["transcode-service", ...]
#   flutter    true si corre Flutter App Tests
#   deploy     true si corre Deploy Scripts
# Con FORCE_ALL=1 (push a develop/main, semanal, manual) todo es verdadero.
#
# Los jobs requeridos por la protección de rama corren siempre y saltan sus pasos: un job
# saltado entero no publica el check con el nombre de la matriz y el PR quedaría bloqueado.
set -euo pipefail

JAVA=(api-gateway auth-service social-service event-service checkin-service
      mosaic-service streaming-service archive-service chat-service media-service)
PYTHON=(transcode-service moderation-service)

declare -A java=() python=() images=()
flutter=false deploy=false all_java=false all_images=false

if [ "${FORCE_ALL:-0}" = 1 ]; then
  all_java=true all_images=true flutter=true deploy=true
  for p in "${PYTHON[@]}"; do python[$p]=1; done
  images[mongo-backup]=1
fi

while IFS= read -r f || [ -n "$f" ]; do
  f=${f%$'\r'}
  [ -z "$f" ] && continue
  case "$f" in
    # El propio CI: se prueba entero
    .github/workflows/ci.yml|.github/scripts/ci-changes.sh)
      all_java=true all_images=true flutter=true deploy=true
      for p in "${PYTHON[@]}"; do python[$p]=1; done
      images[mongo-backup]=1 ;;
    # Reactor, librería común e imagen Java: los 10 servicios (mismos patrones que
    # infrastructure/deploy/changed-services.sh del CD)
    backend/pom.xml|backend/mvnw|backend/mvnw.cmd|backend/.mvn/*|backend/shared-*/*|backend/Dockerfile|backend/Dockerfile.dockerignore)
      all_java=true ;;
    backend/*/*)
      svc=${f#backend/}; svc=${svc%%/*}
      for j in "${JAVA[@]}"; do
        if [ "$j" = "$svc" ]; then java[$svc]=1; fi
      done ;;
    # Fixtures y contrato: los leen pruebas de varios servicios Java y de la app
    docs/api/*)
      all_java=true; flutter=true ;;
    ai-services/transcode-service/*) python[transcode-service]=1 ;;
    ai-services/moderation-service/*) python[moderation-service]=1 ;;
    infrastructure/backup/*) images[mongo-backup]=1; deploy=true ;;
    frontend/*) flutter=true ;;
    # Arranque en prod y escaneo de imágenes
    docker-compose.prod.yml) all_images=true; deploy=true ;;
    .trivyignore) all_images=true ;;
  esac
  case "$f" in
    infrastructure/*|docker-compose*.yml|.github/workflows/*|.env*.example) deploy=true ;;
  esac
done

if [ "$all_java" = true ]; then
  for j in "${JAVA[@]}"; do java[$j]=1; done
fi
for p in "${!python[@]}"; do images[$p]=1; done
# Prod Startup levanta las 10 imágenes Java: si cambia una, se construyen todas
prod=false
if [ "${#java[@]}" -gt 0 ] || [ "$all_images" = true ]; then
  prod=true
  for j in "${JAVA[@]}"; do images[$j]=1; done
fi
if [ "$all_images" = true ]; then
  for p in "${PYTHON[@]}"; do images[$p]=1; done
  images[mongo-backup]=1
fi

json() {
  local first=true out='['
  for k in "$@"; do
    if [ "$first" = true ]; then first=false; else out+=','; fi
    out+="\"$k\""
  done
  printf '%s]' "$out"
}
sorted() { printf '%s\n' "$@" | sort | grep -v '^$' || true; }

# shellcheck disable=SC2046  # una palabra por servicio
{
  echo "java=$(json $(sorted "${!java[@]}"))"
  echo "images=$(json $(sorted "${!images[@]}"))"
  echo "prod=$prod"
  echo "python=$(json $(sorted "${!python[@]}"))"
  echo "flutter=$flutter"
  echo "deploy=$deploy"
}
