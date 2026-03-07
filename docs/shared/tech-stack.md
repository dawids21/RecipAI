# RecipAI - tech stack

## Backend

- Java 25
- Spring Boot 3.5.10
- Spring Framework 6.2.x
- Spring Data JPA
- Spring Validation (Bean Validation)
- Spring Actuator
- Spring Security + OAuth2 Resource Server
- Spring AI 1.1.2
    - Google Genai starter
    - PDF Document Reader
- PostgreSQL 17.5
- Flyway (core + PostgreSQL)
- Lombok 1.18.38
- AWS SDK for Java 2.40.7 (S3)
- Thumbnailator 0.4.21 (image processing)
- Apache Commons IO 2.21.0
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
- Media: image_picker 1.2.0, photo_view 0.15.0
- Platform integration: url_launcher 6.3.2, wakelock_plus 1.3.4
- Local storage: shared_preferences 2.5.4
- HTTP multipart utils: http_parser 4.1.2, mime 2.0.0
- Firebase: firebase_core 4.1.0, firebase_auth 6.0.2, google_sign_in 7.1.1
- Dependency injection: get_it 8.2.0
- UUID generation: uuid 4.0.0
- Collection utilities: collection 1.19.1
- Search: fuzzy 0.5.1
- Internationalization: flutter_localizations (SDK), intl
- Dev tooling: flutter_lints 5.0.0, flutter_test, flutter_launcher_icons 0.14.4

## Deployment

- Private VPS (Backend)
- AWS S3 (Recipe image storage)
- Google Play (Android distribution)
