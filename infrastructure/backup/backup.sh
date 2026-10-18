#!/usr/bin/env bash
# mongodump de todas las bases → store:<BACKUP_BUCKET>/<BACKUP_PREFIX>/vito-<UTC>.archive.gz
# y borra los backups con más de BACKUP_RETENTION_DAYS días. Se ejecuta vía entrypoint.sh.
set -euo pipefail

prefix="${BACKUP_PREFIX:-mongo}"
retention="${BACKUP_RETENTION_DAYS:-14}"
dest="store:${BACKUP_BUCKET}/${prefix}"
name="vito-$(date -u +%Y%m%dT%H%M%SZ).archive.gz"
workdir=$(mktemp -d)
trap 'rm -rf "$workdir"' EXIT

if [[ "${S3_CREATE_BUCKET:-false}" == "true" ]]; then
    rclone mkdir "store:${BACKUP_BUCKET}"
fi

log "mongodump → ${name}"
mongodump --uri="$MONGODB_URI" --gzip --archive="${workdir}/${name}" --quiet

size=$(stat -c %s "${workdir}/${name}")
if (( size == 0 )); then
    log "ERROR: el dump está vacío"
    exit 1
fi

log "subiendo ${size} bytes a ${dest}/"
rclone copyto "${workdir}/${name}" "${dest}/${name}"

# Verificación: el objeto existe en el bucket con el mismo tamaño
remote_size=$(rclone size --json "${dest}/${name}" | sed -E 's/.*"bytes":([0-9]+).*/\1/')
if [[ "$remote_size" != "$size" ]]; then
    log "ERROR: tamaño remoto ${remote_size} ≠ local ${size}"
    exit 1
fi

log "retención: borrando backups con más de ${retention} días"
rclone delete --min-age "${retention}d" --include "vito-*.archive.gz" "${dest}/"

log "OK ${dest}/${name}"
