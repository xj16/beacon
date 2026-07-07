# syntax=docker/dockerfile:1
#
# One multi-stage build for all three Beacon services. Pick the service at build time:
#
#   docker build --build-arg SERVICE=ingest-service  -t beacon/ingest  .
#   docker build --build-arg SERVICE=query-api        -t beacon/query   .
#   docker build --build-arg SERVICE=anomaly-worker   -t beacon/anomaly .
#
# docker-compose.yml wires all three up for you — see the "Run the whole stack" section of the
# README. The build stage caches the Gradle download and dependency resolution across services.

# ---- build stage ----------------------------------------------------------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Copy the wrapper + build scripts first so dependency layers cache when only source changes.
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY common/build.gradle.kts ./common/
COPY ingest-service/build.gradle.kts ./ingest-service/
COPY query-api/build.gradle.kts ./query-api/
COPY anomaly-worker/build.gradle.kts ./anomaly-worker/
RUN chmod +x ./gradlew && ./gradlew --no-daemon help >/dev/null 2>&1 || true

# Now the sources.
COPY . .

ARG SERVICE=ingest-service
RUN chmod +x ./gradlew && ./gradlew --no-daemon :${SERVICE}:bootJar \
    && cp ${SERVICE}/build/libs/*.jar /workspace/app.jar

# ---- runtime stage --------------------------------------------------------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a non-root user.
RUN groupadd -r beacon && useradd -r -g beacon beacon
COPY --from=build /workspace/app.jar /app/app.jar
USER beacon

EXPOSE 8081 8082 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
