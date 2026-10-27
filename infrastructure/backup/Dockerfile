# Backup de MongoDB a almacenamiento S3 (T-I09, ADR-011): R2 en producción, SeaweedFS en local.
#
#   docker build -t vito/mongo-backup infrastructure/backup
#
# mongodump/mongorestore vienen de la imagen oficial (misma versión que el servidor);
# rclone es un binario estático y habla S3 con cualquier proveedor.
FROM rclone/rclone:1.75.2 AS rclone

FROM mongo:7.0

COPY --from=rclone /usr/local/bin/rclone /usr/local/bin/rclone

# Solo se usan mongodump y mongorestore. gosu y el resto de las database tools son binarios Go
# de la imagen base con CVE que Trivy bloquea en cd.yml (hotfix del release 0.1.0). /opt/js-yaml
# solo lo usa el docker-entrypoint.sh de mongo, que esta imagen no ejecuta.
RUN rm -f /usr/local/bin/gosu /usr/bin/bsondump /usr/bin/mongoexport /usr/bin/mongofiles \
        /usr/bin/mongoimport /usr/bin/mongostat /usr/bin/mongotop \
    && rm -rf /opt/js-yaml
COPY backup.sh restore.sh entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/entrypoint.sh

# Sin "mongod": la imagen base arrancaría el servidor
ENTRYPOINT ["entrypoint.sh"]
CMD ["schedule"]
