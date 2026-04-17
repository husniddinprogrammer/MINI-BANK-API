# ─────────────────────────────────────────────────────────────────────────────
# Multi-stage Dockerfile for Mini Banking API
#
# Stage 1 (builder): compiles and packages the fat JAR
# Stage 2 (runtime): minimal JRE image with a non-root user
# ─────────────────────────────────────────────────────────────────────────────

# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy only dependency descriptors first to leverage Docker layer caching.
# If only source files change, Maven does not re-download dependencies.
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Now copy source and build (skip tests — run separately in CI)
COPY src ./src
RUN mvn package -DskipTests -B

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

# Security: run as a non-root user to limit blast radius of a container escape
RUN addgroup -S banking && adduser -S banking -G banking

WORKDIR /app

# Copy the fat JAR from the builder stage
COPY --from=builder /build/target/mini-banking-api-*.jar app.jar

# Change ownership to the non-root user
RUN chown banking:banking app.jar

USER banking

# Expose the application port
EXPOSE 8080

# JVM tuning for containerized environments:
#   -XX:+UseContainerSupport   — respect cgroup memory limits (Docker)
#   -XX:MaxRAMPercentage=75    — use 75% of available container memory for heap
#   -Djava.security.egd       — faster startup on Linux (urandom for non-crypto seeding)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
