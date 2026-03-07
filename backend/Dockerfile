# Multi-stage build for RecipAI Spring Boot API

# Build stage
FROM eclipse-temurin:25-jdk-alpine AS builder
WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Copy and cache dependencies first for better layer caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the application
COPY src ./src
RUN mvn package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Install wget for health checks
RUN apk add --no-cache wget

# Create non-root user and group
RUN addgroup --system recipai && adduser --system recipai --ingroup recipai

# Copy JAR file from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Change ownership of the application files to non-root user
RUN chown recipai:recipai app.jar

# Switch to non-root user
USER recipai:recipai

# Health check configuration
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port 8080
EXPOSE 8080

# Optimized JVM settings for container environment
ENTRYPOINT ["java", "-XX:+UseCompactObjectHeaders", "-XX:MaxRAMPercentage=80.0", "-XX:+UseContainerSupport", "-jar", "app.jar"]