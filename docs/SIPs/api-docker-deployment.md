# SIP: API Docker Deployment

## Goal

- Create a complete Docker containerization solution for the RecipAI Spring Boot API with production-ready best
  practices
- Implement automated GitHub Actions CI/CD pipeline for building and publishing Docker images to GitHub Container
  Registry (GHCR)
- Configure Docker container with non-root user, health checks, and optimized JVM settings for production deployment
- Enable both automatic builds on push to main branch and manual triggering of the build process
- Update project documentation with deployment instructions

## Context

### Documentation and References

- **Spring Boot Dockerfile Best Practices**: https://medium.com/@rohitloke/spring-boot-docker-best-practices-4bf4fdec158
- **Docker Best Practices for Java 2025**: https://www.javaguides.net/2025/02/docker-best-practices-for-java.html
- **GitHub Container Registry Documentation
  **: https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-container-registry
- **Spring Boot Actuator Endpoints**: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
- **Project Backend Documentation**: `docs/backend/backend.md`
- **Backend AI Rules**: `backend/CLAUDE.md`

### Current Codebase Tree

```
recipai/
├── backend/
│   ├── pom.xml                               # Maven build configuration
│   ├── compose.yaml                          # Development PostgreSQL setup
│   ├── src/main/java/xyz/stasiak/recipai/
│   │   └── RecipAiApplication.java           # Main Spring Boot application
│   └── src/main/resources/
│       ├── application.yml                   # Common configuration
│       ├── application-dev.yml               # Development configuration
│       └── application-prod.yml              # Production configuration
├── docs/
│   ├── backend/backend.md                    # Backend documentation
│   └── SIPs/
└── (no .github/ directory exists)
```

### Desired Codebase Tree

```
recipai/
├── backend/
│   ├── Dockerfile                            # Docker configuration for API
│   ├── pom.xml                               # Maven build configuration
│   ├── compose.yaml                          # Development PostgreSQL setup
│   ├── src/main/java/xyz/stasiak/recipai/
│   │   └── RecipAiApplication.java           # Main Spring Boot application
│   └── src/main/resources/
│       ├── application.yml                   # Common configuration (updated with actuator)
│       ├── application-dev.yml               # Development configuration
│       └── application-prod.yml              # Production configuration
├── .github/
│   └── workflows/
│       └── docker-build.yml                 # GitHub Actions workflow
├── docs/
│   ├── backend/backend.md                    # Backend documentation (updated)
│   └── SIPs/
```

### Known Gotchas of Our Codebase and Library Quirks

#### Codebase Specific:

- **Java Version**: Project uses Java 24 (latest version) - ensure Docker base image supports this
- **Spring Boot Version**: 3.5.5 with Spring AI 1.0.0 dependencies
- **Production Profile**: Default active profile is `prod` as defined in `application.yml`
- **Database Dependencies**: Uses PostgreSQL with Flyway migrations enabled
- **OAuth2 Security**: JWT resource server configuration for Firebase Authentication
- **Environment Variables**: Production requires `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
  `SPRING_DATASOURCE_PASSWORD`, `SPRING_AI_API_KEY`

#### Spring Boot Actuator:

- **Health Endpoint**: `/actuator/health` is enabled by default when `spring-boot-starter-actuator` is included
- **Default Exposure**: Only `/health` and `/info` endpoints are exposed over web by default
- **Security**: Health endpoint is accessible without authentication by default

#### Docker Best Practices:

- **Multi-stage Builds**: Essential for smaller final image size and security
- **Non-root User**: Required for production security compliance
- **JVM Container Optimization**: Use `-XX:MaxRAMPercentage=80.0` and `-XX:+UseContainerSupport`
- **Health Checks**: Use `wget` instead of `curl` to avoid installing additional packages

#### GitHub Actions & GHCR:

- **Authentication**: Use `GITHUB_TOKEN` instead of personal access tokens
- **Image Naming**: Must follow pattern `ghcr.io/owner/repository:tag`
- **Permissions**: Workflow needs `contents: read` and `packages: write` permissions

## Implementation Plan

### Tasks

```
Task 1: Create Dockerfile for Spring Boot API
  Action: CREATE
  File: backend/Dockerfile
  Changes:
    - [ ] Use multi-stage build with eclipse-temurin:24-jdk-alpine for build stage
    - [ ] Use eclipse-temurin:24-jre-alpine for runtime stage
    - [ ] Create non-root user 'recipai' with dedicated group
    - [ ] Configure JVM optimization flags for container environment
    - [ ] Add health check using wget to /actuator/health endpoint
    - [ ] Expose port 8080
    - [ ] Follow Docker layer caching best practices (copy pom.xml first)

Task 2: Update Spring Boot Actuator configuration
  Action: MODIFY
  File: backend/src/main/resources/application.yml
  Changes:
    - [ ] Ensure management.endpoints.web.exposure.include includes health
    - [ ] Configure management.endpoint.health.show-details for Docker health checks
    - [ ] Set appropriate server port configuration for containers

Task 3: Create GitHub Actions workflow
  Action: CREATE
  File: .github/workflows/docker-build.yml
  Changes:
    - [ ] Configure triggers for push to main and manual workflow_dispatch
    - [ ] Set up Java 24 with Maven caching
    - [ ] Build Spring Boot application with Maven
    - [ ] Build and tag Docker image with both latest and commit SHA
    - [ ] Authenticate with GHCR using GITHUB_TOKEN
    - [ ] Push image to GitHub Container Registry
    - [ ] Add proper permissions and error handling

Task 4: Update backend documentation
  Action: MODIFY
  File: docs/backend/backend.md
  Changes:
    - [ ] Add new section "Building and Deploying the API"
    - [ ] Document Docker build process and commands
    - [ ] Explain environment variables needed for production
    - [ ] Include instructions for pulling and running from GHCR
    - [ ] Add health check endpoint information
```

### Per Task Pseudocode

#### Task 1: Dockerfile Creation

```dockerfile
# Multi-stage build approach
FROM eclipse-temurin:24-jdk-alpine AS builder
WORKDIR /app

# Copy and cache dependencies first
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests

# Runtime stage
FROM eclipse-temurin:24-jre-alpine
WORKDIR /app

# Install wget for health checks
RUN apk add --no-cache wget

# Create non-root user
RUN addgroup --system recipai && adduser --system recipai --ingroup recipai

# Copy JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Switch to non-root user
USER recipai:recipai

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Optimized JVM settings
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=80.0", "-XX:+UseContainerSupport", "-jar", "app.jar"]
```

#### Task 3: GitHub Actions Workflow

```yaml
name: Build and Push Docker Image

on:
  push:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 24
        uses: actions/setup-java@v4
        with:
          java-version: '24'
          distribution: 'temurin'
          cache: maven

      - name: Build with Maven
        run: mvn clean package -DskipTests
        working-directory: ./backend

      - name: Log in to GHCR
        run: echo "${{ secrets.GITHUB_TOKEN }}" | docker login ghcr.io -u ${{ github.actor }} --password-stdin

      - name: Extract metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ghcr.io/${{ github.repository }}/api
          tags: |
            type=ref,event=branch
            type=sha,prefix={{branch}}-
            type=raw,value=latest

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
```

## Validation

### Syntax and Style

```bash
# Run these FIRST - fix any errors before proceeding
cd backend
mvn compile

# Expected: No compilation errors. If errors, READ the error and fix.
```

### Unit Tests

```bash
# Run and iterate until passing:
cd backend
mvn test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Integration Tests

```bash
# Run and iterate until passing:
cd backend
mvn test
# If failing: Read error, understand root cause, fix code, re-run (never mock to pass)
```

### Docker Build Validation

```bash
# Test Docker build locally
cd backend
docker build -t recipai-api:test .

# Test container health
docker run -d --name recipai-test -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/test \
  -e SPRING_DATASOURCE_USERNAME=test \
  -e SPRING_DATASOURCE_PASSWORD=test \
  -e SPRING_AI_API_KEY=test \
  recipai-api:test

# Wait for startup and test health
sleep 30
curl -f http://localhost:8080/actuator/health

# Cleanup
docker stop recipai-test && docker rm recipai-test
```

### GitHub Actions Validation

```bash
# Test workflow manually after push
# 1. Push changes to main branch
# 2. Check GitHub Actions tab for successful workflow execution
# 3. Verify image appears in GitHub Packages
# 4. Test manual trigger via workflow_dispatch
```

## Integration Points

- **GitHub Container Registry**: Images will be available at `ghcr.io/{username}/recipai/api:latest`
- **Spring Boot Actuator**: Health endpoint `/actuator/health` exposed for container orchestration
- **Environment Variables**: Production deployment requires database and API key configuration
- **Port Exposure**: Container exposes port 8080 for HTTP traffic
- **Database Connection**: Container needs external PostgreSQL database for production use

## Documentation

- **docs/backend/backend.md**: Add section "Building and Deploying the API" with Docker instructions
- **CLAUDE.md**: Consider adding Docker deployment context if frequently referenced

## Final Validation Checklist

- [ ] Correct syntax (Maven compiles without errors)
- [ ] Correct style (follows existing codebase patterns)
- [ ] All tests pass (unit and integration tests)
- [ ] Manual test successful (Docker container starts and health check passes)
- [ ] Error cases handled gracefully (container fails safely if dependencies unavailable)
- [ ] Logs are informative but not verbose (Spring Boot default logging)
- [ ] Documentation updated (backend.md includes deployment instructions)
- [ ] GitHub Actions workflow triggers correctly (both automatic and manual)
- [ ] Docker image builds and pushes to GHCR successfully
- [ ] Container runs with non-root user (security compliance)
- [ ] Health checks work properly (container orchestration compatibility)

**SIP Confidence Score: 9/10**

This SIP provides comprehensive context and step-by-step implementation guidance. The only potential challenge might be
Java 24 compatibility with some base images, but eclipse-temurin provides official support. All patterns follow current
2025 best practices and integrate well with the existing Spring Boot application structure.