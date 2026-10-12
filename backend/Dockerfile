# syntax=docker/dockerfile:1.7
#
# Imagen común para los 10 servicios Spring Boot. El contexto de build es la carpeta
# del servicio y este archivo se pasa con -f:
#
#   docker build -f backend/Dockerfile -t vito/auth-service backend/auth-service
#
# Lo que no entra en la imagen está en Dockerfile.dockerignore (junto a este archivo).

# ── Build ───────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /src

# Dependencias primero: esta capa solo se invalida si cambia el pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2,sharing=locked ./mvnw -B -q package -DskipTests \
 && cp target/*.jar /app.jar

# ── Runtime ─────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy

# curl solo para el healthcheck (GET /actuator/health/readiness)
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --no-create-home vito

WORKDIR /app
COPY --from=build /app.jar app.jar
USER vito

# Respeta los límites de memoria del contenedor
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
