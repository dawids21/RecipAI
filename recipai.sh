#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Keystore paths
KEYSTORES_DIR="$SCRIPT_DIR/scripts/keystores"
ANDROID_DIR="$SCRIPT_DIR/mobile/android"

DEBUG_KEYSTORE_GPG="$KEYSTORES_DIR/debug_keystore.jks.gpg"
DEBUG_KEYSTORE="$ANDROID_DIR/debug_keystore.jks"
UPLOAD_KEYSTORE_GPG="$KEYSTORES_DIR/upload_keystore.jks.gpg"
UPLOAD_KEYSTORE="$ANDROID_DIR/upload_keystore.jks"
UPLOAD_KEY_PROPERTIES="$ANDROID_DIR/upload-key.properties"

# Keystore helpers
decrypt_keystore() {  # <src.gpg> <dest>
    gpg --yes --output "$2" --decrypt "$1"
}

encrypt_keystore() {  # <src> <dest.gpg>
    gpg --yes --symmetric --cipher-algo AES256 --output "$2" "$1"
}

prompt_password() {  # <prompt-text> -> password on stdout
    local value
    read -r -s -p "$1: " value
    echo >&2
    printf '%s' "$value"
}

confirm() {  # <question> -> exit status, y/N default N
    local answer
    read -r -p "$1 [y/N] " answer
    [[ "$answer" =~ ^[Yy]$ ]]
}

# Commands
setup() {
    if [[ ! -f "$DEBUG_KEYSTORE_GPG" ]]; then
        echo -e "${RED}Encrypted debug keystore missing at scripts/keystores/debug_keystore.jks.gpg — is the repo up to date?${NC}"
        exit 1
    fi
    echo -e "${YELLOW}Decrypting the shared debug keystore (passphrase is in the password manager)${NC}"
    if ! decrypt_keystore "$DEBUG_KEYSTORE_GPG" "$DEBUG_KEYSTORE"; then
        echo -e "${RED}Decryption failed — wrong passphrase, or gpg is not installed${NC}"
        exit 1
    fi
    echo -e "${GREEN}Wrote mobile/android/debug_keystore.jks${NC}"
}

encrypt_key_debug() {
    if [[ ! -f "$DEBUG_KEYSTORE" ]]; then
        echo -e "${RED}Nothing to encrypt at mobile/android/debug_keystore.jks${NC}"
        exit 1
    fi
    if [[ -f "$DEBUG_KEYSTORE_GPG" ]]; then
        if ! confirm "Replace scripts/keystores/debug_keystore.jks.gpg? The current key will only exist in git history."; then
            echo "Aborted."
            exit 1
        fi
    fi
    if ! encrypt_keystore "$DEBUG_KEYSTORE" "$DEBUG_KEYSTORE_GPG"; then
        echo -e "${RED}Encryption failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}Commit the new ciphertext and store the passphrase in the password manager${NC}"
}

encrypt_key_upload() {
    if [[ ! -f "$UPLOAD_KEYSTORE" ]]; then
        echo -e "${RED}Nothing to encrypt at mobile/android/upload_keystore.jks${NC}"
        exit 1
    fi
    if [[ -f "$UPLOAD_KEYSTORE_GPG" ]]; then
        if ! confirm "Replace scripts/keystores/upload_keystore.jks.gpg? The current key will only exist in git history."; then
            echo "Aborted."
            exit 1
        fi
    fi
    if ! encrypt_keystore "$UPLOAD_KEYSTORE" "$UPLOAD_KEYSTORE_GPG"; then
        echo -e "${RED}Encryption failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}Commit the new ciphertext and store the passphrase in the password manager${NC}"
}

ensure_upload_key() {
    if [[ ! -f "$UPLOAD_KEYSTORE" ]]; then
        if [[ ! -f "$UPLOAD_KEYSTORE_GPG" ]]; then
            echo -e "${RED}Encrypted upload keystore missing at scripts/keystores/upload_keystore.jks.gpg${NC}"
            exit 1
        fi
        echo -e "${YELLOW}Decrypting the upload keystore (passphrase is in the password manager)${NC}"
        if ! decrypt_keystore "$UPLOAD_KEYSTORE_GPG" "$UPLOAD_KEYSTORE"; then
            echo -e "${RED}Decryption failed — wrong passphrase, or gpg is not installed${NC}"
            exit 1
        fi
    fi

    if [[ ! -f "$UPLOAD_KEY_PROPERTIES" ]]; then
        local password
        password="$(prompt_password "Upload keystore password")"
        cat > "$UPLOAD_KEY_PROPERTIES" << PROPS
storePassword=$password
keyPassword=$password
keyAlias=upload
storeFile=$UPLOAD_KEYSTORE
PROPS
        chmod 600 "$UPLOAD_KEY_PROPERTIES"
        echo -e "${GREEN}Wrote upload-key.properties — later builds will not prompt again${NC}"
    fi
}

build_mobile() {
    ensure_upload_key
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
    setup                     One-time: decrypt the shared debug keystore
    encrypt-key-debug         Re-encrypt a local debug keystore into scripts/keystores/
    encrypt-key-upload        Re-encrypt a local upload keystore into scripts/keystores/
    build-mobile              Build Android AAB with production configuration
    release-internal-mobile   Upload the built AAB to the Play Console internal track
    help                      Show this help message

Setup (one-time, required by release-internal-mobile):
    python3 -m venv scripts/.venv
    scripts/.venv/bin/pip install -r scripts/requirements.txt
    Place the Play service account key at scripts/play-service-account.json

Examples:
    recipai.sh setup
    recipai.sh build-mobile
    recipai.sh encrypt-key-debug
    recipai.sh encrypt-key-upload
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
    setup)
        setup
        ;;
    encrypt-key-debug)
        encrypt_key_debug
        ;;
    encrypt-key-upload)
        encrypt_key_upload
        ;;
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
