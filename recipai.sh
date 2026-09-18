#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ANDROID_DIR="$SCRIPT_DIR/mobile/android"

DEBUG_KEYSTORE="$ANDROID_DIR/debug_keystore.jks"
UPLOAD_KEYSTORE="$ANDROID_DIR/upload_keystore.jks"
UPLOAD_KEY_PROPERTIES="$ANDROID_DIR/upload-key.properties"

# Release paths
AAB_PATH="$SCRIPT_DIR/mobile/build/app/outputs/bundle/release/app-release.aab"
VENV_PYTHON="$SCRIPT_DIR/scripts/.venv/bin/python3"
PLAY_SERVICE_ACCOUNT="$SCRIPT_DIR/scripts/play-service-account.json"
PLAY_STORE_URL="https://play.google.com/store/apps/details?id=xyz.stasiak.recipai"

# Backend paths
BACKEND_DIR="$SCRIPT_DIR/backend"
BACKEND_LOG="$BACKEND_DIR/target/backend-run.log"
BACKEND_PID="$BACKEND_DIR/target/backend-run.pid"
BACKEND_PORT="${SERVER_PORT:-8080}"
BACKEND_URL="http://localhost:$BACKEND_PORT"

# Keystore helpers
keystore_sha1() {  # <keystore> <storepass> -> SHA-1 line on stdout
    keytool -list -v -keystore "$1" -storepass "$2" 2>/dev/null \
        | grep -m1 "SHA1:" | sed "s/^[[:space:]]*//"
}

keystore_instructions() {  # <filename>
    echo "Copy $1 from its secure location into mobile/android/."
    echo "It is gitignored and must never be committed."
}

write_upload_properties() {  # <password>
    # Created empty and locked down before the password is written into it.
    : > "$UPLOAD_KEY_PROPERTIES"
    chmod 600 "$UPLOAD_KEY_PROPERTIES"
    cat > "$UPLOAD_KEY_PROPERTIES" << PROPS
storePassword=$1
keyPassword=$1
keyAlias=upload
storeFile=$UPLOAD_KEYSTORE
PROPS
}

prompt_password() {  # <prompt-text> -> password on stdout
    local value
    read -r -s -p "$1: " value
    echo >&2
    printf '%s' "$value"
}

# Backend helpers
backend_env() {
    export SPRING_PROFILES_ACTIVE=dev
    # SPRING_AI_API_KEY has no default in application.yml, so an unset value aborts
    # context creation. A dummy is enough to boot; only /extract/** actually calls out.
    export SPRING_AI_API_KEY="${SPRING_AI_API_KEY:-dummy-key-for-local}"
    export SERVER_PORT="$BACKEND_PORT"
}

backend_healthy() {
    curl -fsS --max-time 3 "$BACKEND_URL/actuator/health" 2>/dev/null | grep -q '"status":"UP"'
}

# Commands
setup() {
    if [[ ! -f "$DEBUG_KEYSTORE" ]]; then
        echo -e "${RED}mobile/android/debug_keystore.jks is missing${NC}"
        keystore_instructions debug_keystore.jks
        exit 1
    fi
    echo -e "${GREEN}Debug keystore is in place. Check the fingerprint against its secure location:${NC}"
    echo "  debug_keystore.jks  $(keystore_sha1 "$DEBUG_KEYSTORE" android)"
}

ensure_upload_key() {
    if [[ ! -f "$UPLOAD_KEYSTORE" ]]; then
        echo -e "${RED}mobile/android/upload_keystore.jks is missing${NC}"
        keystore_instructions upload_keystore.jks
        exit 1
    fi

    if [[ ! -f "$UPLOAD_KEY_PROPERTIES" ]]; then
        local password
        password="$(prompt_password "Upload keystore password")"
        # Proven against the keystore before it is written, so a typo cannot
        # leave a broken properties file behind for later builds to pick up.
        if [[ -z "$(keystore_sha1 "$UPLOAD_KEYSTORE" "$password")" ]]; then
            echo -e "${RED}Could not read upload_keystore.jks — wrong password, or the wrong file${NC}"
            exit 1
        fi
        write_upload_properties "$password"
        echo -e "${GREEN}Wrote upload-key.properties — later builds will not prompt again${NC}"
    fi
}

build_mobile() {
    ensure_upload_key
    echo -e "${YELLOW}Building AAB for RecipAI...${NC}"
    cd "$SCRIPT_DIR/mobile"
    flutter build appbundle --dart-define=API_BASE_URL=https://recipai-api.stasiak.xyz
    echo -e "${GREEN}AAB build completed successfully!${NC}"
    echo "AAB location: $AAB_PATH"
}

ensure_venv() {
    if [[ ! -x "$VENV_PYTHON" ]]; then
        echo -e "${RED}venv not found at scripts/.venv${NC}"
        echo "Create it once with:"
        echo "    python3 -m venv scripts/.venv"
        echo "    scripts/.venv/bin/pip install -r scripts/requirements.txt"
        exit 1
    fi
}

abort() {  # <message>
    echo -e "${RED}$1${NC}" >&2
    exit 1
}

release_internal_mobile() {
    [[ $# -eq 0 ]] || abort "release-internal-mobile takes no arguments (got: $*)"
    cd "$SCRIPT_DIR"

    # Preflight: everything that can fail is checked before anything is published.
    ensure_venv
    [[ -f "$PLAY_SERVICE_ACCOUNT" ]] \
        || abort "scripts/play-service-account.json is missing — copy the Play service account key there"

    command -v gh > /dev/null 2>&1 || abort "gh is not on PATH — install the GitHub CLI"
    local gh_status
    gh_status="$(gh auth status 2>&1)" || abort "gh is not authenticated — run: gh auth login"
    grep -q "Token scopes:.*'repo'" <<< "$gh_status" \
        || abort "the gh token lacks the 'repo' scope — run: gh auth refresh -s repo"

    git rev-parse --git-dir > /dev/null 2>&1 || abort "not inside the git repository"
    [[ -z "$(git status --porcelain)" ]] \
        || abort "the working tree is dirty — commit or stash before releasing"
    local head_sha short_sha
    head_sha="$(git rev-parse HEAD)"
    short_sha="$(git rev-parse --short HEAD)"
    [[ -n "$(git branch -r --contains "$head_sha" --list 'origin/*' 2> /dev/null)" ]] \
        || abort "HEAD ($short_sha) is not on origin — push it first"

    [[ -f "$AAB_PATH" ]] \
        || abort "AAB not found at ${AAB_PATH#$SCRIPT_DIR/} — build it first: ./recipai.sh build-mobile"

    local version version_name version_code
    version="$(grep -m1 '^version:' mobile/pubspec.yaml | awk '{print $2}')"
    version_name="${version%%+*}"
    version_code="${version##*+}"
    [[ "$version" == *+* && -n "$version_name" && "$version_code" =~ ^[0-9]+$ ]] \
        || abort "could not parse a <name>+<code> version from mobile/pubspec.yaml (got '$version')"

    local tag="v$version_name+$version_code"
    if gh release view "$tag" > /dev/null 2>&1; then
        abort "GitHub release $tag already exists — bump the version in mobile/pubspec.yaml"
    fi

    local repo_info repo visibility aab_size
    repo_info="$(gh repo view --json nameWithOwner,visibility \
        -q '.nameWithOwner + " " + (.visibility | ascii_downcase)')" \
        || abort "could not read the repository from origin — is it on GitHub?"
    read -r repo visibility <<< "$repo_info"
    aab_size="$(awk -v bytes="$(stat -c %s "$AAB_PATH")" 'BEGIN { printf "%.1f", bytes / 1048576 }')"

    echo "About to release RecipAI $version_name ($version_code):"
    echo "  Play internal track   upload app-release.aab ($aab_size MiB)"
    echo "  GitHub release        $repo, tag $tag at $short_sha ($visibility, empty body)"
    [[ -t 0 ]] || abort "stdin is not a terminal — this command needs an interactive confirmation"
    local reply
    read -r -p "Proceed? [y/N] " reply
    [[ "$reply" == [yY] ]] || abort "aborted — nothing was published"

    local report
    report="$(mktemp)"
    trap 'rm -f "$report"' EXIT

    echo -e "${YELLOW}Publishing to the Play Console internal track...${NC}"
    "$VENV_PYTHON" scripts/play_service.py \
        --track internal --version-code "$version_code" --report "$report"

    # The tag follows the version name Play reports for the release, not the pubspec one.
    local play_name apk_path
    play_name="$(grep -m1 '^version_name=' "$report" | cut -d= -f2-)"
    apk_path="$(grep -m1 '^apk_path=' "$report" | cut -d= -f2-)"
    [[ -n "$play_name" && -n "$apk_path" && -f "$apk_path" ]] \
        || abort "play_service.py did not report a downloaded APK"

    local play_tag="v$play_name+$version_code"
    echo -e "${YELLOW}Creating GitHub release $play_tag...${NC}"
    local release_url
    release_url="$(gh release create "$play_tag" "$apk_path" \
        --title "RecipAI $play_name ($version_code)" \
        --notes "" \
        --target "$head_sha" \
        --latest)"

    echo -e "${GREEN}Released RecipAI $play_name ($version_code)${NC}"
    echo "  Play:   $PLAY_STORE_URL"
    echo "  GitHub: $release_url"
}

run_backend() {
    cd "$BACKEND_DIR"
    backend_env
    echo -e "${YELLOW}Running backend (profile dev, port $BACKEND_PORT) — Ctrl+C to stop${NC}"
    exec ./mvnw spring-boot:run
}

start_backend() {
    if backend_healthy; then
        echo -e "${GREEN}backend already UP at $BACKEND_URL${NC}"
        return 0
    fi

    if [[ -f "$BACKEND_PID" ]] && kill -0 "$(cat "$BACKEND_PID")" 2>/dev/null; then
        echo -e "${RED}a managed run (pgid $(cat "$BACKEND_PID")) exists but is not healthy — it may still be booting. Check $BACKEND_LOG, or run stop-backend first.${NC}"
        exit 1
    fi

    if curl -sS --max-time 3 -o /dev/null "$BACKEND_URL" 2>/dev/null; then
        echo -e "${RED}something is already listening on port $BACKEND_PORT but it is not a healthy backend this script manages. Free the port, or set SERVER_PORT.${NC}"
        exit 1
    fi

    if ! docker info >/dev/null 2>&1; then
        echo -e "${RED}the docker daemon is not reachable. The app starts its own postgres via backend/compose.yaml and cannot boot without it.${NC}"
        exit 1
    fi

    mkdir -p "$BACKEND_DIR/target"
    : > "$BACKEND_LOG"

    (
        cd "$BACKEND_DIR"
        backend_env
        # New session, so maven and the app JVM it forks can be signalled as one group on stop.
        setsid bash -c 'echo $$ > "'"$BACKEND_PID"'"; exec ./mvnw -q spring-boot:run' \
            > "$BACKEND_LOG" 2>&1 &
    )

    echo -e "${YELLOW}starting backend (profile dev, port $BACKEND_PORT) ...${NC}"

    local waited=0
    while [[ "$waited" -lt 180 ]]; do
        if backend_healthy; then
            echo -e "${GREEN}backend UP at $BACKEND_URL (log: $BACKEND_LOG)${NC}"
            return 0
        fi
        if [[ "$waited" -gt 5 ]] && { [[ ! -f "$BACKEND_PID" ]] || ! kill -0 "$(cat "$BACKEND_PID" 2>/dev/null)" 2>/dev/null; }; then
            echo "--- last 40 lines of $BACKEND_LOG ---" >&2
            tail -n 40 "$BACKEND_LOG" >&2
            rm -f "$BACKEND_PID"
            echo -e "${RED}the backend process exited during startup — see the log above.${NC}"
            exit 1
        fi
        sleep 2
        waited=$((waited + 2))
    done

    echo "--- last 40 lines of $BACKEND_LOG ---" >&2
    tail -n 40 "$BACKEND_LOG" >&2
    echo -e "${RED}backend did not become healthy within 180s. It may still be booting; inspect the log above.${NC}"
    exit 1
}

stop_backend() {
    local pgid=""
    if [[ -f "$BACKEND_PID" ]]; then
        pgid="$(cat "$BACKEND_PID" 2>/dev/null || true)"
        if [[ -n "$pgid" ]] && ! kill -0 "$pgid" 2>/dev/null; then
            pgid=""
        fi
    fi

    if [[ -z "$pgid" ]]; then
        rm -f "$BACKEND_PID"
        if backend_healthy; then
            echo -e "${RED}a backend is healthy on port $BACKEND_PORT but was not started by this script, so it will not be killed. Stop it wherever it was launched.${NC}"
            exit 1
        fi
        echo -e "${GREEN}backend is not running${NC}"
        return 0
    fi

    echo -e "${YELLOW}stopping backend (pgid $pgid) ...${NC}"
    kill -TERM -- "-$pgid" 2>/dev/null || true

    local waited=0
    while [[ "$waited" -lt 60 ]]; do
        if ! kill -0 "$pgid" 2>/dev/null; then
            rm -f "$BACKEND_PID"
            echo -e "${GREEN}stopped${NC}"
            return 0
        fi
        sleep 1
        waited=$((waited + 1))
    done

    echo -e "${YELLOW}graceful stop timed out after 60s, sending SIGKILL${NC}"
    kill -KILL -- "-$pgid" 2>/dev/null || true
    rm -f "$BACKEND_PID"
    echo -e "${RED}killed — check 'docker ps' for a leftover backend-postgres container${NC}"
}

show_help() {
    cat << EOF
RecipAI CLI

Usage: recipai.sh <command> [options]

Commands:
    setup                     Check the debug keystore is in place and print its fingerprint
    build-mobile              Build Android AAB with production configuration
    release-internal-mobile   Publish the built AAB to the Play internal track, then publish the
                              APK Play signs from it as a GitHub release
    run-backend               Run the backend in the foreground on the dev profile (Ctrl+C to stop)
    start-backend             Start the backend detached, waiting until it is healthy
    stop-backend              Stop a backend started with start-backend
    help                      Show this help message

Signing keystores (kept in a secure location, one entry each):
    debug_keystore.jks   copy into mobile/android/, then run ./recipai.sh setup
    upload_keystore.jks  copy into mobile/android/ for release builds only;
                         build-mobile generates upload-key.properties from it

Setup (one-time, required by release-internal-mobile):
    python3 -m venv scripts/.venv
    scripts/.venv/bin/pip install -r scripts/requirements.txt
    Place the Play service account key at scripts/play-service-account.json
    Install the GitHub CLI and run: gh auth login   (the token needs the 'repo' scope)

Examples:
    recipai.sh setup
    recipai.sh build-mobile
    recipai.sh release-internal-mobile
    recipai.sh run-backend
    recipai.sh start-backend
    recipai.sh stop-backend
    recipai.sh help
EOF
}

# Main
if [[ $# -eq 0 ]]; then
    show_help
    exit 1
fi

case "$1" in
    setup)
        setup
        ;;
    build-mobile)
        build_mobile
        ;;
    release-internal-mobile)
        shift
        release_internal_mobile "$@"
        ;;
    run-backend)
        run_backend
        ;;
    start-backend)
        start_backend
        ;;
    stop-backend)
        stop_backend
        ;;
    help|-h|--help)
        show_help
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        show_help
        exit 1
        ;;
esac
