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

show_help() {
    cat << EOF
RecipAI CLI

Usage: recipai.sh <command> [options]

Commands:
    build-mobile    Build Android AAB with production configuration
    help            Show this help message

Examples:
    recipai.sh build-mobile
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
    help|-h|--help)
        show_help
        ;;
    *)
        echo -e "${RED}Unknown command: $1${NC}"
        show_help
        exit 1
        ;;
esac
