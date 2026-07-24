# ── Stage 1: Build ────────────────────────────────────────────────────────────
# We use the official Gradle image so no local Gradle install is needed.
FROM gradle:8.10-jdk21 AS build

WORKDIR /app

# Copy dependency files first — Docker caches this layer separately.
# If only source files change, dependencies are NOT re-downloaded.
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN gradle dependencies --no-daemon || true

# Copy source and build the fat JAR
COPY src ./src
RUN gradle shadowJar --no-daemon

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Minimal JRE image — much smaller than the build image (~200MB vs ~1GB)
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Non-root user for security — never run as root in production.
# chown /app BEFORE switching to non-root so COPY commands (which run as root)
# produce files owned by the app user, and the JVM can write temp files.
RUN addgroup -S recommendly && \
    adduser -S recommendly -G recommendly && \
    chown -R recommendly:recommendly /app

USER recommendly

# Copy the built JAR from the build stage
COPY --from=build --chown=recommendly:recommendly /app/build/libs/recommendly-backend-all.jar app.jar

# Copy SQL migration files directly into the runtime image.
# This bypasses Flyway's classpath scanner (which can misidentify filenames
# inside fat JARs) and uses a reliable filesystem path instead.
COPY --from=build --chown=recommendly:recommendly /app/src/main/resources/db/migration /app/db/migration

EXPOSE 8080

# JVM tuning for containers:
# -XX:+UseContainerSupport  — respect Docker memory limits
# -XX:MaxRAMPercentage=75   — use up to 75% of container memory for heap
CMD ["java", \
     "-XX:+UseContainerSupport", \
     "-XX:MaxRAMPercentage=75", \
     "-jar", "app.jar"]
