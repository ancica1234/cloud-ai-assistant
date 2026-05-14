
# ── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Run ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy the built jar
COPY --from=build /app/target/cloud-ai-assistant-1.0.0.jar app.jar

# Create dirs for docs and vectorstore
RUN mkdir -p /app/docs /app/data

# Docs are mounted at runtime (see docker-compose.yml)
VOLUME ["/app/docs", "/app/data"]

# Port
EXPOSE 8081

# Health check for AWS/ECS
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3     CMD wget -qO- http://localhost:8081/api/ai/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "-Dapp.ai.vectorstore.path=/app/data/vectorstore.json", "-Dapp.ai.docs.path=/app/docs", "app.jar"]
