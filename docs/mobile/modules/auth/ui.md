# Auth — UI

## Screens

- Login Screen (`login_screen.dart`) - Welcome screen with the RecipAI logo and title:
  - *Default build*: a Google Sign-In button, a loading state while signing in, and an error message when sign-in
    fails.
  - *Dev-auth build* (see `module.md` → Services and State): a "Dev user name" field and a "Login" button replace the
    Google button. Login stays disabled until the field is non-empty; a refused name is reported as "Dev user name
    must contain only letters, digits, and -._~+/".

## Flow

#### Authentication Flow

1. **App Launch** → Authentication check:
    - **If unauthenticated** → Login Screen (`/login`)
    - **If authenticated** → Main Screen (`/`) showing Recipes tab
2. **Login Screen → Google Sign-In Tap** (default build) → Authentication process → Main Screen (`/`)
3. **Login Screen → Login Tap** (dev-auth build) → signed in as that name → Main Screen (`/`)
4. **Main Screen → Logout Tap** → Confirmation dialog → Sign out → Login Screen (`/login`)
