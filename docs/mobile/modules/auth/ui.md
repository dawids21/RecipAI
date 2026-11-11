# Auth — UI

## Screens

- Login Screen (`login_screen.dart`) - Welcome screen with Google Sign-In button, app branding (RecipAI logo and
  title), loading states during authentication, and error handling for sign-in failures. Takes an optional
  `DevAuthService`; when one is injected it renders a "Dev user name" field and a "Login" button **instead of** the
  Google button, since a dev-auth build never initialises Firebase. The Login button stays disabled until the field
  is non-empty, and a refused name (see `module.md` → Services and State) is reported as "Dev user name must contain
  only letters, digits, and -._~+/"

## Flow

#### Authentication Flow

1. **App Launch** → Authentication check:
    - **If unauthenticated** → Login Screen (`/login`)
    - **If authenticated** → Main Screen (`/`) showing Recipes tab
2. **Login Screen → Google Sign-In Tap** (default build) → Authentication process → Main Screen (`/`)
3. **Login Screen → Login Tap** (dev-auth build) → name persisted as the bearer token → Main Screen (`/`)
4. **Main Screen → Logout Tap** → Confirmation dialog → Sign out → Login Screen (`/login`)
