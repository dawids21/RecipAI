#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Commands
build_mobile() {
    echo -e "${YELLOW}Building AAB for RecipAI...${NC}"
    cd "$SCRIPT_DIR/mobile"
    flutter build appbundle --dart-define=API_BASE_URL=https://recipai.stasiak.xyz
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

show_help() {
    cat << EOF
RecipAI CLI

Usage: recipai.sh <command> [options]

Commands:
    build-mobile             Build Android AAB with production configuration
    release-internal-mobile  Upload the built AAB to the Play Console internal track
    help                     Show this help message

Setup (one-time, required by release-internal-mobile):
    python3 -m venv scripts/.venv
    scripts/.venv/bin/pip install -r scripts/requirements.txt
    Place the Play service account key at scripts/play-service-account.json

Examples:
    recipai.sh build-mobile
    recipai.sh release-internal-mobile
    recipai.sh help
EOF
}

# Main
if [[ $# -eq 0 ]]; then
    show_help
    exit 1
fi

case "$1" in
    build-mobile)
        build_mobile
        ;;
    release-internal-mobile)
        shift
        release_internal_mobile "$@"
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
