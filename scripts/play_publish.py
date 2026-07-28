#!/usr/bin/env python3
"""Upload a signed Android App Bundle to the Google Play internal testing track.

This talks to the Google Play Developer API v3 directly via the official
google-api-python-client. It deliberately does only one thing: take an existing,
already-signed .aab and assign it to a track (default: internal).

Authentication uses a Google Cloud service account that has been granted access
in the Play Console (Setup -> API access). Place the downloaded key file at
scripts/play-service-account.json.

Usage:
    python3 scripts/play_publish.py \
        --aab mobile/build/app/outputs/bundle/release/app-release.aab

    python3 scripts/play_publish.py --track alpha --aab path/to/app.aab
"""

import argparse
import os
import sys

DEFAULT_PACKAGE = "xyz.stasiak.recipai"
DEFAULT_TRACK = "internal"
DEFAULT_AAB = "mobile/build/app/outputs/bundle/release/app-release.aab"
SERVICE_ACCOUNT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "play-service-account.json"
)
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
# Resumable upload chunk size; must be a multiple of 256 KiB.
CHUNK_SIZE = 4 * 1024 * 1024


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--aab",
        default=DEFAULT_AAB,
        help=f"Path to the signed .aab (default: {DEFAULT_AAB})",
    )
    parser.add_argument(
        "--track",
        default=DEFAULT_TRACK,
        help=f"Release track (default: {DEFAULT_TRACK})",
    )
    parser.add_argument(
        "--package",
        default=DEFAULT_PACKAGE,
        help=f"Application id (default: {DEFAULT_PACKAGE})",
    )
    parser.add_argument(
        "--status",
        default="completed",
        choices=["completed", "draft", "inProgress", "halted"],
        help="Release status (default: completed)",
    )
    return parser.parse_args()


def fail(message: str) -> "NoReturn":  # type: ignore[name-defined]
    print(f"error: {message}", file=sys.stderr)
    sys.exit(1)


def build_service(service_account_path: str):
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except ImportError:
        fail(
            "missing dependencies. Install with:\n"
            "    pip install -r scripts/requirements.txt"
        )

    credentials = service_account.Credentials.from_service_account_file(
        service_account_path, scopes=SCOPES
    )
    # cache_discovery=False avoids a noisy warning and a file-cache dependency.
    return build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)


def main() -> None:
    args = parse_args()

    if not os.path.isfile(SERVICE_ACCOUNT_PATH):
        fail(f"service account key not found: {SERVICE_ACCOUNT_PATH}")
    if not os.path.isfile(args.aab):
        fail(
            f"aab not found: {args.aab}\n"
            "Build it first (e.g. ./recipai.sh build-mobile)."
        )

    from googleapiclient.errors import HttpError

    service = build_service(SERVICE_ACCOUNT_PATH)
    edits = service.edits()

    try:
        edit = edits.insert(body={}, packageName=args.package).execute()
        edit_id = edit["id"]
        print(f"opened edit {edit_id}")

        from googleapiclient.http import MediaFileUpload

        size_mb = os.path.getsize(args.aab) / (1024 * 1024)
        print(f"uploading {args.aab} ({size_mb:.1f} MiB)")

        # Chunked so upload progress is visible; the default 100 MiB chunk would
        # send the whole bundle in one silent request.
        media = MediaFileUpload(
            args.aab,
            mimetype="application/octet-stream",
            chunksize=CHUNK_SIZE,
            resumable=True,
        )
        request = edits.bundles().upload(
            packageName=args.package, editId=edit_id, media_body=media
        )
        bundle = None
        while bundle is None:
            status, bundle = request.next_chunk()
            if status is not None:
                print(f"  {status.progress() * 100:5.1f}%", flush=True)
        version_code = bundle["versionCode"]
        print(f"uploaded bundle versionCode {version_code}")

        edits.tracks().update(
            packageName=args.package,
            editId=edit_id,
            track=args.track,
            body={
                "track": args.track,
                "releases": [
                    {
                        "versionCodes": [str(version_code)],
                        "status": args.status,
                    }
                ],
            },
        ).execute()
        print(f"assigned versionCode {version_code} to track '{args.track}'")

        edits.commit(packageName=args.package, editId=edit_id).execute()
        print(
            f"committed: version {version_code} released to '{args.track}' "
            f"({args.status})."
        )
    except HttpError as error:
        fail(f"Play API request failed: {error}")


if __name__ == "__main__":
    main()
