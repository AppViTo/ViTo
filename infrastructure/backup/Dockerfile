# Backup de MongoDB a almacenamiento S3 (T-I09, ADR-011): R2 en producción, SeaweedFS en local.
#
#   docker build -t vito/mongo-backup infrastructure/backup
#
# mongodump/mongorestore vienen de la imagen oficial (misma versión que el servidor);
# rclone es un binario estático y habla S3 con cualquier proveedor.
FROM rclone/rclone:1.68.2 AS rclone

FROM mongo:7.0

COPY --from=rclone /usr/local/bin/rclone /usr/local/bin/rclone
COPY backup.sh restore.sh entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/entrypoint.sh

# Sin "mongod": la imagen base arrancaría el servidor
ENTRYPOINT ["entrypoint.sh"]
CMD ["schedule"]
