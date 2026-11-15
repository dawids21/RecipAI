#!/usr/bin/env python3
"""Copy a Firebase ID token for the production API to the clipboard.

The token belongs to the developer's own Google account, the same identity the
mobile app signs in with. Every run signs in through the browser; nothing is
stored, so no long-lived credential is left on the machine. The ID token is never
printed: it goes to the clipboard only.

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

from google_auth_oauthlib.flow import InstalledAppFlow

# Google Cloud Console → APIs & Services → Credentials → "Browser key (auto created by Firebase)".
# Public by design; the key in google-services.json is restricted to the Android app.
FIREBASE_WEB_API_KEY = ""
# The project's "Desktop app" OAuth client. Google does not treat a desktop client's secret as
# confidential (it ships inside every copy of the app), so both values are committed.
OAUTH_CLIENT_ID = ""
OAUTH_CLIENT_SECRET = ""
SCOPES = ["openid", "https://www.googleapis.com/auth/userinfo.email"]
SIGN_IN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithIdp"


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
    try:
        subprocess.run(
            command,
            input=text.encode(),
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=True,
        )
    except subprocess.CalledProcessError as e:
        fail(f"Copying to the clipboard failed ({command[0]} exited with {e.returncode})")


# Firebase

def firebase_api_key() -> str:
    if not FIREBASE_WEB_API_KEY:
        fail("FIREBASE_WEB_API_KEY in scripts/prod_token.py is empty — copy the Browser key "
             "from Google Cloud Console → APIs & Services → Credentials")
    return FIREBASE_WEB_API_KEY


def post_json(url: str, api_key: str, body: dict) -> dict:
    request = urllib.request.Request(
        f"{url}?key={api_key}",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json"},
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
    except urllib.error.URLError as e:
        raise FirebaseError(f"network error: {e.reason}") from None


def google_id_token() -> str:
    if not (OAUTH_CLIENT_ID and OAUTH_CLIENT_SECRET):
        fail("OAUTH_CLIENT_ID or OAUTH_CLIENT_SECRET in scripts/prod_token.py is empty — copy "
             "them from the Desktop app OAuth client in Google Cloud Console → APIs & Services "
             "→ Credentials")
    print("Opening the browser to sign in with Google...", file=sys.stderr)
    flow = InstalledAppFlow.from_client_config(
        {
            "installed": {
                "client_id": OAUTH_CLIENT_ID,
                "client_secret": OAUTH_CLIENT_SECRET,
                "auth_uri": "https://accounts.google.com/o/oauth2/auth",
                "token_uri": "https://oauth2.googleapis.com/token",
            }
        },
        scopes=SCOPES,
    )
    # "online" stops Google from issuing a refresh token; the library defaults to "offline".
    credentials = flow.run_local_server(port=0, open_browser=True, access_type="online")
    if not credentials.id_token:
        fail("Google sign-in returned no ID token")
    return credentials.id_token


def firebase_id_token(api_key: str, google_token: str) -> str:
    data = post_json(SIGN_IN_URL, api_key, {
        "postBody": urllib.parse.urlencode({"id_token": google_token, "providerId": "google.com"}),
        "requestUri": "http://localhost",
        "returnSecureToken": True,
    })
    # The response also carries a Firebase refresh token; it is deliberately discarded.
    return data["idToken"]


def jwt_claims(token: str) -> dict:
    payload = token.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


def main() -> None:
    clipboard = clipboard_command()
    api_key = firebase_api_key()

    try:
        id_token = firebase_id_token(api_key, google_id_token())
    except FirebaseError as e:
        fail(f"Firebase sign-in failed ({e.code})")

    copy_to_clipboard(clipboard, id_token)
    claims = jwt_claims(id_token)
    expires = time.strftime("%H:%M", time.localtime(claims["exp"]))
    print(f"Copied a production token for {claims.get('email', claims['sub'])} (expires {expires})")


if __name__ == "__main__":
    main()
