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
    echo "AAB location: $SCRIPT_DIR/mobile/build/app/outputs/bundle/release/app-release.aab"
}

release_internal_mobile() {
    echo -e "${YELLOW}Uploading AAB to Play Console internal track...${NC}"
    cd "$SCRIPT_DIR"
    local venv_python="$SCRIPT_DIR/scripts/.venv/bin/python3"
    if [[ ! -x "$venv_python" ]]; then
        echo -e "${RED}venv not found at scripts/.venv${NC}"
        echo "Create it once with:"
        echo "    python3 -m venv scripts/.venv"
        echo "    scripts/.venv/bin/pip install -r scripts/requirements.txt"
        exit 1
    fi
    "$venv_python" scripts/play_publish.py --track internal "$@"
    echo -e "${GREEN}Upload completed successfully!${NC}"
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
    release-internal-mobile   Upload the built AAB to the Play Console internal track
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
