# RecipAI Mobile

Flutter mobile application for managing daily cooking recipes.

## Configuration

The app supports multiple ways to configure the API base URL:

### 1. Environment Variables (Highest Priority)

Set the `API_BASE_URL` environment variable when running the app:

```bash
flutter run --dart-define=API_BASE_URL=http://server:8080
```

### 2. Configuration File (Medium Priority)

Edit `assets/config/app_config.json` to change the API settings:

```json
{
  "apiBaseUrl": "http://server:8080"
}
```

### 3. Default Fallback (Lowest Priority)

If no configuration is provided, the app uses `http://localhost:8080` as the default.

## Getting Started

1. Ensure Flutter is installed and configured
2. Install dependencies: `flutter pub get`
3. Configure your API URL (see Configuration section above)
4. Run the app: `flutter run`

## Development

- Run `flutter analyze` to check for code issues
- Run `flutter test` to execute tests
- The app loads configuration on startup, so restart after changing config files

## API Integration

The app connects to a Spring Boot backend.
Make sure your backend server is running and accessible at the configured URL.