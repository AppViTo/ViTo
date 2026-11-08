# Backup de MongoDB a almacenamiento S3 (T-I09, ADR-011): R2 en producción, SeaweedFS en local.
#
#   docker build -t vito/mongo-backup infrastructure/backup
#
# mongodump/mongorestore vienen de la imagen oficial (misma versión que el servidor);
# rclone es un binario estático y habla S3 con cualquier proveedor.
FROM rclone/rclone:1.76.0 AS rclone

FROM mongo:7.0

COPY --from=rclone /usr/local/bin/rclone /usr/local/bin/rclone

# Solo se usan mongodump y mongorestore. gosu y el resto de las database tools son binarios Go
# de la imagen base con CVE que Trivy bloquea en cd.yml (hotfix del release 0.1.0). /opt/js-yaml
# solo lo usa el docker-entrypoint.sh de mongo, que esta imagen no ejecuta.
# Primero los parches de seguridad de Ubuntu que la imagen base aún no trae (apt 2.4.14, CVE
# HIGH con arreglo; T-I27), igual que la imagen Java. Solo con las fuentes de Ubuntu: el repo
# de MongoDB cambiaría la versión de las tools y volvería a instalar las que se borran abajo.
RUN apt-get update -o Dir::Etc::sourceparts=/dev/null \
    && apt-get upgrade -y --no-install-recommends -o Dir::Etc::sourceparts=/dev/null \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /usr/local/bin/gosu /usr/bin/bsondump /usr/bin/mongoexport /usr/bin/mongofiles \
        /usr/bin/mongoimport /usr/bin/mongostat /usr/bin/mongotop \
    && rm -rf /opt/js-yaml
COPY backup.sh restore.sh entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/entrypoint.sh

# Sin "mongod": la imagen base arrancaría el servidor
ENTRYPOINT ["entrypoint.sh"]
CMD ["schedule"]
