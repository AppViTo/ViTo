#!/usr/bin/env bash
# Comandos del contenedor de backup:
#   schedule            (por defecto) backup diario a la hora BACKUP_AT (UTC, HH:MM)
#   backup              un backup ahora y termina
#   restore [args...]   ver restore.sh
#   list                lista los backups del bucket
set -euo pipefail

# rclone se configura solo por entorno: remoto "store" con API S3.
# Mismas credenciales que media-service (el token de R2 cubre vito-media y vito-backups).
export RCLONE_CONFIG_STORE_TYPE=s3
export RCLONE_CONFIG_STORE_PROVIDER="${S3_PROVIDER:-Other}"
export RCLONE_CONFIG_STORE_ENDPOINT="${S3_ENDPOINT:?definir S3_ENDPOINT}"
export RCLONE_CONFIG_STORE_REGION="${S3_REGION:-us-east-1}"
export RCLONE_CONFIG_STORE_ACCESS_KEY_ID="${S3_ACCESS_KEY:?definir S3_ACCESS_KEY}"
export RCLONE_CONFIG_STORE_SECRET_ACCESS_KEY="${S3_SECRET_KEY:?definir S3_SECRET_KEY}"
export RCLONE_CONFIG_STORE_FORCE_PATH_STYLE="${S3_PATH_STYLE:-true}"
# El token de R2 no puede crear buckets: sin S3_CREATE_BUCKET no se comprueban ni se crean
if [[ "${S3_CREATE_BUCKET:-false}" == "true" ]]; then
    export RCLONE_CONFIG_STORE_NO_CHECK_BUCKET=false
else
    export RCLONE_CONFIG_STORE_NO_CHECK_BUCKET=true
fi
# Toda la configuración va por entorno: sin archivo rclone.conf
export RCLONE_CONFIG=/dev/null

: "${MONGODB_URI:?definir MONGODB_URI (usuario root, authSource=admin)}"
: "${BACKUP_BUCKET:?definir BACKUP_BUCKET}"

log() { echo "$(date -u +%Y-%m-%dT%H:%M:%SZ) [mongo-backup] $*"; }
export -f log

seconds_until() {
    local now target
    now=$(date -u +%s)
    target=$(date -u -d "today $1" +%s)
    if (( target <= now )); then
        target=$(date -u -d "tomorrow $1" +%s)
    fi
    echo $(( target - now ))
}

cmd="${1:-schedule}"
shift || true

case "$cmd" in
    backup)  exec backup.sh ;;
    restore) exec restore.sh "$@" ;;
    list)    exec rclone lsl "store:${BACKUP_BUCKET}/${BACKUP_PREFIX:-mongo}/" ;;
    schedule)
        at="${BACKUP_AT:-03:00}"
        log "programado a diario a las ${at} UTC; retención ${BACKUP_RETENTION_DAYS:-14} días"
        while true; do
            wait_s=$(seconds_until "$at")
            log "próximo backup en ${wait_s}s"
            sleep "$wait_s"
            # Un fallo no detiene el programador: se registra y se reintenta mañana
            backup.sh || log "ERROR: el backup falló (código $?)"
        done
        ;;
    *)
        echo "uso: entrypoint.sh [schedule|backup|restore|list]" >&2
        exit 64
        ;;
esac
