# RecipAI - tech stack

## Backend

- Java 24
- Spring Boot 3.5.5
- Spring Framework 6.2.x (e.g., Core/Web 6.2.9)
- Spring Data JPA
- Spring Validation (Bean Validation)
- Spring Actuator
- Spring Security + OAuth2 Resource Server
- Spring AI 1.0.0
    - OpenAI starter
    - PDF Document Reader
- PostgreSQL 17.5
- Flyway (core + PostgreSQL)
- Lombok
- AWS SDK for Java 2.40.7 (S3)
- Thumbnailator 0.4.20 (image processing)
- Testcontainers (JUnit Jupiter, PostgreSQL)
- Docker Compose integration (Spring Boot Docker Compose, Spring AI Docker Compose)
- Maven (spring-boot-maven-plugin, compiler with annotation processors)

## Mobile

- Dart SDK 3.8.1
- Flutter 3.32
- Routing: Go Router 16.1.0
- Networking: HTTP 1.1.0
- Logging: logging 1.2.0
- Web content: webview_flutter 4.13.0
- UI: flutter_speed_dial 7.0.0, cupertino_icons 1.0.8
- Media: image_picker 1.2.0
- Platform integration: url_launcher 6.3.2
- HTTP multipart utils: http_parser 4.1.2, mime 2.0.0
- Firebase: firebase_core 4.1.0, firebase_auth 6.0.2, google_sign_in 7.1.1
- Dependency injection: get_it 8.2.0
- UUID generation: uuid 4.0.0
- Dev tooling: flutter_lints 5.0.0, flutter_test, flutter_launcher_icons 0.14.4

## Deployment

- Private VPS (Backend)
- AWS S3 (Recipe image storage)
- Google Play (Android distribution)
