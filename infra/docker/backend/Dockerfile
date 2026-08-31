# syntax=docker/dockerfile:1
#
# Backend image for the receipts app — Spring Boot fat JAR on eclipse-temurin.
# Build context is the repository root (see infra/compose.yml: backend.build.context: ..),
# so every COPY below is relative to the repo root, not this file's directory.
#
# Mirrors investing-app's infra/backend/Dockerfile pattern (multi-stage Maven build →
# minimal JRE runtime, non-root user), adapted for this app's flat single-module Maven
# project (no reactor / shared-java module to build alongside it — see ADR-001).

# ── Stage 1: Build the Spring Boot fat JAR ─────────────────────────────────────
FROM maven:3.9.16-eclipse-temurin-25-alpine AS builder
WORKDIR /app

# Copy the POM first so dependency resolution is cached in its own layer until it changes.
COPY backend/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline

COPY backend/src ./src
RUN mvn -B package -DskipTests

# ── Stage 2: Minimal runtime image ──────────────────────────────────────────────
FROM eclipse-temurin:25.0.4_7-jre-alpine
WORKDIR /app

# Non-root runtime user. Numeric UID/GID (not names) per Hadolint DL3066 — a named user
# may not resolve the same way on every host. Pre-create the image-storage mount point
# owned by that UID so Docker seeds the receipts-images named volume with the right
# permissions on first mount (a fresh named volume inherits ownership from the image
# directory at the same path).
RUN addgroup -g 1000 -S spring \
    && adduser -u 1000 -S spring -G spring \
    && mkdir -p /data/receipts \
    && chown -R 1000:1000 /app /data/receipts

USER 1000:1000

COPY --from=builder --chown=spring:spring /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
