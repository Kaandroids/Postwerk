# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src/ src/
RUN ./mvnw package -DskipTests -B

# Stage 2: Run (production-hardened)
FROM eclipse-temurin:21-jre-alpine

# Create non-root user for security
RUN addgroup -g 1000 appuser && adduser -D -u 1000 -G appuser appuser

# Install curl for health checks
RUN apk add --no-cache curl

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Run as non-root
USER appuser

EXPOSE 8080

# Container health check — verifies app + DB + Redis connectivity
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/api/v1/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
