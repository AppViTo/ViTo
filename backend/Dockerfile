# syntax=docker/dockerfile:1.7
#
# Imagen común para los 10 servicios Spring Boot. El contexto de build es backend/ (por
# shared-web, T-B18) y el servicio se elige con SERVICE:
#
#   docker build -f backend/Dockerfile --build-arg SERVICE=auth-service -t vito/auth-service backend
#
# Lo que no entra en la imagen está en Dockerfile.dockerignore (junto a este archivo).

# ── Build ───────────────────────────────────────────────────────
FROM eclipse-temurin:21.0.13_9-jdk-jammy AS build
ARG SERVICE
RUN test -n "$SERVICE" || (echo "Falta --build-arg SERVICE=<carpeta del servicio>" && exit 1)
WORKDIR /src

# No se usa el agregador backend/pom.xml: exigiría los poms de los 11 módulos. Cada paso
# instala shared-web y construye el servicio con -f.
COPY .mvn/ .mvn/
COPY mvnw ./
RUN chmod +x mvnw

# Dependencias primero: esta capa solo se invalida si cambia un pom.xml. shared-web se
# instala sin código (jar vacío) solo para que go-offline del servicio lo resuelva.
COPY shared-web/pom.xml shared-web/
COPY ${SERVICE}/pom.xml ${SERVICE}/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q -f shared-web/pom.xml install -DskipTests \
 && ./mvnw -B -q -f ${SERVICE}/pom.xml dependency:go-offline

# shared-web se instala en el mismo RUN que el package: el caché de /root/.m2 no viaja con
# el caché de capas del CI, y una capa de install cacheada dejaría al package sin el jar
COPY shared-web/src/ shared-web/src/
COPY ${SERVICE}/src/ ${SERVICE}/src/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -q -f shared-web/pom.xml install -DskipTests \
 && ./mvnw -B -q -f ${SERVICE}/pom.xml package -DskipTests \
 && cp ${SERVICE}/target/*.jar /app.jar

# ── Runtime ─────────────────────────────────────────────────────
FROM eclipse-temurin:21.0.13_9-jre-jammy

# upgrade: parches de seguridad de Ubuntu que la imagen base aún no trae (p. ej. apt 2.4.14
# con un CVE HIGH ya corregido, que corta el Trivy de cd.yml). curl solo para el
# healthcheck (GET /actuator/health/readiness)
RUN apt-get update \
 && apt-get upgrade -y --no-install-recommends \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --no-create-home vito

WORKDIR /app
COPY --from=build /app.jar app.jar
USER vito

# Respeta los límites de memoria del contenedor
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
