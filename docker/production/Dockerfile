# syntax=docker/dockerfile:1
#
# Build context is the repository root, not this directory:
#   docker build -f docker/production/Dockerfile .
# The production compose file sets `context: ../..` to match.

# ---------- build ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies resolve in a cached layer, so source-only edits skip the download.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Tests are skipped here: they need a Docker daemon for Testcontainers, which is
# not available inside a build. Run `./mvnw verify` in CI before building.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/*.jar /build/app.jar

# ---------- runtime ----------
# Ubuntu-based rather than -alpine: eclipse-temurin publishes no arm64 alpine
# JRE, so the alpine variant cannot build on Apple Silicon.
FROM eclipse-temurin:17-jre AS runtime

# curl is only here for the container healthcheck; this base ships without it.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app

COPY --from=build /build/app.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

# Container memory limits are respected instead of the host's total RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
