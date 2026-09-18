#!/usr/bin/env python3
"""Publish a signed Android App Bundle to Google Play and fetch the APK Play builds from it.

This talks to the Google Play Developer API v3 directly via the official
google-api-python-client. It takes an existing, already-signed .aab, assigns it
to a track (default: internal), then waits for Play to generate the universal
APK for that version and downloads it. The downloaded APK is signed with the
Play app-signing key, which is what makes it installable alongside a Play
install; an APK built locally carries the upload key instead.

Authentication uses a Google Cloud service account that has been granted access
in the Play Console (Setup -> API access). Place the downloaded key file at
scripts/play-service-account.json.

Usage:
    python3 scripts/play_service.py \
        --aab mobile/build/app/outputs/bundle/release/app-release.aab

    python3 scripts/play_service.py --track alpha --aab path/to/app.aab --no-apk

Passing --version-code makes the run resumable: if that version code already
sits on the track, the upload is skipped and only the APK is fetched.
"""

import argparse
import os
import sys
import time

DEFAULT_PACKAGE = "xyz.stasiak.recipai"
DEFAULT_TRACK = "internal"
DEFAULT_AAB = "mobile/build/app/outputs/bundle/release/app-release.aab"
DEFAULT_APK_DIR = "mobile/build/app/outputs/github"
SERVICE_ACCOUNT_PATH = os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "play-service-account.json"
)
SCOPES = ["https://www.googleapis.com/auth/androidpublisher"]
# Resumable transfer chunk size; must be a multiple of 256 KiB.
CHUNK_SIZE = 4 * 1024 * 1024
# Play builds the universal APK a few minutes after the edit commits.
POLL_INTERVAL_SECONDS = 15
POLL_TIMEOUT_SECONDS = 20 * 60


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
    parser.add_argument(
        "--version-code",
        type=int,
        help="Expected version code. When it is already on the track the upload is skipped.",
    )
    parser.add_argument(
        "--apk-dir",
        default=DEFAULT_APK_DIR,
        help=f"Directory for the downloaded universal APK (default: {DEFAULT_APK_DIR})",
    )
    parser.add_argument(
        "--no-apk",
        action="store_true",
        help="Stop after the upload instead of waiting for and downloading the APK",
    )
    parser.add_argument(
        "--report",
        help="Write version_name, version_code and apk_path as key=value lines to this file",
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


def track_release(service, package: str, track: str, version_code: int):
    """The release on `track` holding `version_code`, or None.

    Reading a track needs an edit, so one is opened and discarded again; nothing
    is changed by this.
    """
    edits = service.edits()
    edit_id = edits.insert(body={}, packageName=package).execute()["id"]
    try:
        info = edits.tracks().get(
            packageName=package, editId=edit_id, track=track
        ).execute()
    finally:
        edits.delete(packageName=package, editId=edit_id).execute()

    for release in info.get("releases", []):
        if str(version_code) in release.get("versionCodes", []):
            return release
    return None


def upload_bundle(service, args: argparse.Namespace) -> int:
    """Upload the .aab, assign it to the track and commit. Returns the version code."""
    from googleapiclient.http import MediaFileUpload

    edits = service.edits()
    edit = edits.insert(body={}, packageName=args.package).execute()
    edit_id = edit["id"]
    print(f"opened edit {edit_id}")

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
    return version_code


def universal_apk_download_id(service, package: str, version_code: int):
    """The download id of the generated universal APK, or None while Play is still building it."""
    from googleapiclient.errors import HttpError

    try:
        response = service.generatedapks().list(
            packageName=package, versionCode=version_code
        ).execute()
    except HttpError as error:
        # Until generation starts there is nothing to list.
        if error.resp.status == 404:
            return None
        raise

    for generated in response.get("generatedApks", []):
        download_id = generated.get("generatedUniversalApk", {}).get("downloadId")
        if download_id:
            return download_id
    return None


def wait_for_universal_apk(service, package: str, version_code: int) -> str:
    deadline = time.monotonic() + POLL_TIMEOUT_SECONDS
    while True:
        download_id = universal_apk_download_id(service, package, version_code)
        if download_id:
            return download_id
        if time.monotonic() >= deadline:
            fail(
                f"Play has not generated the universal APK for versionCode {version_code} "
                f"within {POLL_TIMEOUT_SECONDS // 60} minutes.\n"
                "Re-run the same command later — the upload is skipped and the wait resumes."
            )
        print("  waiting for Play to generate the universal APK ...", flush=True)
        time.sleep(POLL_INTERVAL_SECONDS)


def download_apk(service, package: str, version_code: int, download_id: str, path: str) -> None:
    from googleapiclient.http import MediaIoBaseDownload

    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    print(f"downloading universal APK to {path}")

    request = service.generatedapks().download_media(
        packageName=package, versionCode=version_code, downloadId=download_id
    )
    # Written aside and renamed only once complete, so an interrupted run can
    # never leave a truncated APK behind to be published.
    partial = f"{path}.part"
    with open(partial, "wb") as handle:
        downloader = MediaIoBaseDownload(handle, request, chunksize=CHUNK_SIZE)
        done = False
        while not done:
            status, done = downloader.next_chunk()
            if status is not None:
                print(f"  {status.progress() * 100:5.1f}%", flush=True)
    os.replace(partial, path)


def write_report(path: str, version_name: str, version_code: int, apk_path: str) -> None:
    with open(path, "w") as handle:
        handle.write(f"version_name={version_name}\n")
        handle.write(f"version_code={version_code}\n")
        if apk_path:
            handle.write(f"apk_path={apk_path}\n")


def main() -> None:
    args = parse_args()

    if not os.path.isfile(SERVICE_ACCOUNT_PATH):
        fail(f"service account key not found: {SERVICE_ACCOUNT_PATH}")

    from googleapiclient.errors import HttpError

    service = build_service(SERVICE_ACCOUNT_PATH)

    try:
        version_code = args.version_code
        release = (
            track_release(service, args.package, args.track, version_code)
            if version_code is not None
            else None
        )

        if release is not None:
            print(
                f"versionCode {version_code} is already on track '{args.track}' "
                "— skipping upload"
            )
        else:
            if not os.path.isfile(args.aab):
                fail(
                    f"aab not found: {args.aab}\n"
                    "Build it first (e.g. ./recipai.sh build-mobile)."
                )
            version_code = upload_bundle(service, args)
            release = track_release(service, args.package, args.track, version_code)
            if release is None:
                fail(
                    f"versionCode {version_code} was committed but is not on track "
                    f"'{args.track}' — check the Play Console."
                )

        version_name = release.get("name")
        if not version_name:
            fail(
                f"Play reports no release name for versionCode {version_code} on track "
                f"'{args.track}' — name the release in the Play Console and re-run."
            )
        print(f"version name {version_name}")

        apk_path = ""
        if not args.no_apk:
            download_id = wait_for_universal_apk(service, args.package, version_code)
            apk_path = os.path.join(
                args.apk_dir, f"recipai-{version_name}-{version_code}.apk"
            )
            download_apk(service, args.package, version_code, download_id, apk_path)
            print(f"downloaded {apk_path} ({os.path.getsize(apk_path)} bytes)")

        if args.report:
            write_report(args.report, version_name, version_code, apk_path)
    except HttpError as error:
        fail(f"Play API request failed: {error}")


if __name__ == "__main__":
    main()
