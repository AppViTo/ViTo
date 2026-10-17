#!/usr/bin/env bash
# Restaura un backup del bucket en MongoDB. Se ejecuta vía entrypoint.sh:
#
#   restore.sh --yes [latest|vito-<UTC>.archive.gz] [--uri <mongodb-uri>]
#
# --uri       restaurar en otra instancia (p. ej. una vacía para la prueba mensual de
#             ADR-011). Por defecto, MONGODB_URI.
# --yes       obligatorio: usa --drop, que reemplaza las colecciones existentes.
set -euo pipefail

prefix="${BACKUP_PREFIX:-mongo}"
src="store:${BACKUP_BUCKET}/${prefix}"
target_uri="$MONGODB_URI"
which="latest"
confirmed=false

while (( $# )); do
    case "$1" in
        --yes) confirmed=true ;;
        --uri) target_uri="${2:?--uri necesita un valor}"; shift ;;
        -*)    echo "opción desconocida: $1" >&2; exit 64 ;;
        *)     which="$1" ;;
    esac
    shift
done

if [[ "$confirmed" != "true" ]]; then
    echo "restore reemplaza las colecciones existentes (--drop). Repetir con --yes." >&2
    exit 64
fi

if [[ "$which" == "latest" ]]; then
    # Los nombres llevan la fecha UTC: el orden lexicográfico es el cronológico
    which=$(rclone lsf --include "vito-*.archive.gz" "${src}/" | sort | tail -n 1)
    if [[ -z "$which" ]]; then
        log "ERROR: no hay backups en ${src}/"
        exit 1
    fi
fi

workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

log "descargando ${src}/${which}"
rclone copyto "${src}/${which}" "${workdir}/${which}"

log "mongorestore --drop desde ${which}"
# admin.* fuera: los usuarios los crea MONGO_INITDB_* en la instancia destino
mongorestore --uri="$target_uri" --gzip --archive="${workdir}/${which}" --drop \
    --nsExclude='admin.*' --quiet

log "OK restaurado ${which}"
