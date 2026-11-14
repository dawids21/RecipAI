#!/usr/bin/env python3
"""Copy a Firebase ID token for the production API to the clipboard.

The token belongs to the developer's own Google account, the same identity the
mobile app signs in with. The first run signs in through the browser; later runs
exchange a saved Firebase refresh token for a fresh one-hour ID token. The refresh
token is kept in the system keyring, or in an owner-only file under
$XDG_STATE_HOME/recipai/ when no keyring is available. The ID token is never
printed: it goes to the clipboard only.

Requires scripts/prod-oauth-client.json, the "Desktop app" OAuth client of the
Firebase project downloaded from Google Cloud Console.

Usage:
    python3 scripts/prod_token.py
"""

import base64
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

import keyring
import keyring.errors
from google_auth_oauthlib.flow import InstalledAppFlow

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OAUTH_CLIENT_PATH = os.path.join(SCRIPT_DIR, "prod-oauth-client.json")
# The Firebase project's Web API key (public by design). The key in google-services.json is
# restricted to the Android app, so Firebase refuses it from a desktop.
FIREBASE_WEB_API_KEY = ""
STATE_DIR = os.path.join(
    os.environ.get("XDG_STATE_HOME") or os.path.expanduser("~/.local/state"), "recipai"
)
REFRESH_TOKEN_FILE = os.path.join(STATE_DIR, "prod-refresh-token")
KEYRING_SERVICE = "recipai"
KEYRING_USER = "prod-refresh-token"
SCOPES = ["openid", "https://www.googleapis.com/auth/userinfo.email"]
SIGN_IN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp"
REFRESH_URL = "https://securetoken.googleapis.com/v1/token"
# Firebase answers with these when a saved refresh token can never work again.
DEAD_REFRESH_TOKEN_ERRORS = {
    "TOKEN_EXPIRED",
    "INVALID_REFRESH_TOKEN",
    "USER_DISABLED",
    "USER_NOT_FOUND",
}


class FirebaseError(Exception):
    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


def fail(message: str) -> None:
    print(message, file=sys.stderr)
    sys.exit(1)


# Clipboard

def clipboard_command() -> list[str]:
    if os.environ.get("WAYLAND_DISPLAY") and shutil.which("wl-copy"):
        return ["wl-copy"]
    if os.environ.get("DISPLAY"):
        if shutil.which("xclip"):
            return ["xclip", "-selection", "clipboard"]
        if shutil.which("xsel"):
            return ["xsel", "--clipboard", "--input"]
    fail(
        "No clipboard tool found — install wl-clipboard (Wayland) or xclip (X11).\n"
        "The token is never printed, so there is no fallback."
    )


def copy_to_clipboard(command: list[str], text: str) -> None:
    # The tools fork to keep serving the selection; with inherited pipes the call would hang.
    subprocess.run(
        command,
        input=text.encode(),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=True,
    )


# Refresh token storage: keyring first, file fallback

def load_refresh_token() -> str | None:
    try:
        token = keyring.get_password(KEYRING_SERVICE, KEYRING_USER)
        if token:
            return token
    except keyring.errors.KeyringError:
        pass
    try:
        with open(REFRESH_TOKEN_FILE) as f:
            return f.read().strip() or None
    except FileNotFoundError:
        return None


def save_refresh_token(token: str) -> str:
    try:
        keyring.set_password(KEYRING_SERVICE, KEYRING_USER, token)
        remove_file()
        return "system keyring"
    except keyring.errors.KeyringError:
        pass
    os.makedirs(STATE_DIR, mode=0o700, exist_ok=True)
    # Created owner-only before the token is written into it.
    fd = os.open(REFRESH_TOKEN_FILE, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        f.write(token)
    return REFRESH_TOKEN_FILE


def forget_refresh_token() -> None:
    try:
        keyring.delete_password(KEYRING_SERVICE, KEYRING_USER)
    except keyring.errors.KeyringError:
        pass
    remove_file()


def remove_file() -> None:
    try:
        os.remove(REFRESH_TOKEN_FILE)
    except FileNotFoundError:
        pass


# Firebase

def firebase_api_key() -> str:
    if not FIREBASE_WEB_API_KEY:
        fail("FIREBASE_WEB_API_KEY in scripts/prod_token.py is empty — copy the Web API key "
             "from Firebase Console → Project settings → General")
    return FIREBASE_WEB_API_KEY


def post(url: str, api_key: str, body: bytes, content_type: str) -> dict:
    request = urllib.request.Request(
        f"{url}?key={api_key}", data=body, headers={"Content-Type": content_type}
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except urllib.error.HTTPError as e:
        try:
            code = json.load(e)["error"]["message"]
        except (ValueError, KeyError, TypeError):
            code = f"HTTP {e.code}"
        raise FirebaseError(code) from None


def refresh(api_key: str, refresh_token: str) -> tuple[str, str]:
    body = urllib.parse.urlencode(
        {"grant_type": "refresh_token", "refresh_token": refresh_token}
    ).encode()
    data = post(REFRESH_URL, api_key, body, "application/x-www-form-urlencoded")
    return data["id_token"], data["refresh_token"]


def sign_in(api_key: str) -> tuple[str, str]:
    if not os.path.isfile(OAUTH_CLIENT_PATH):
        fail(
            "scripts/prod-oauth-client.json is missing — download the Desktop app OAuth "
            "client of the Firebase project from Google Cloud Console and place it there"
        )
    print("Opening the browser to sign in with Google...", file=sys.stderr)
    flow = InstalledAppFlow.from_client_secrets_file(OAUTH_CLIENT_PATH, scopes=SCOPES)
    credentials = flow.run_local_server(port=0, open_browser=True)
    if not credentials.id_token:
        fail("Google sign-in returned no ID token")
    body = json.dumps({
        "postBody": urllib.parse.urlencode(
            {"id_token": credentials.id_token, "providerId": "google.com"}
        ),
        "requestUri": "http://localhost",
        "returnSecureToken": True,
    }).encode()
    data = post(SIGN_IN_URL, api_key, body, "application/json")
    return data["idToken"], data["refreshToken"]


def jwt_claims(token: str) -> dict:
    payload = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


def main() -> None:
    clipboard = clipboard_command()
    api_key = firebase_api_key()

    id_token = None
    saved = load_refresh_token()
    if saved:
        try:
            id_token, refresh_token = refresh(api_key, saved)
        except FirebaseError as e:
            if e.code not in DEAD_REFRESH_TOKEN_ERRORS:
                fail(f"Refreshing the token failed ({e.code}); the saved sign-in was kept")
            print(f"The saved sign-in no longer works ({e.code}).", file=sys.stderr)
            forget_refresh_token()
    if id_token is None:
        try:
            id_token, refresh_token = sign_in(api_key)
        except FirebaseError as e:
            fail(f"Firebase sign-in failed ({e.code})")
        print(f"Saved the sign-in in: {save_refresh_token(refresh_token)}", file=sys.stderr)
    elif refresh_token != saved:
        save_refresh_token(refresh_token)

    copy_to_clipboard(clipboard, id_token)
    claims = jwt_claims(id_token)
    expires = time.strftime("%H:%M", time.localtime(claims["exp"]))
    print(f"Copied a production token for {claims.get('email', claims['sub'])} (expires {expires})")


if __name__ == "__main__":
    main()
