# Upload Key Management

## Overview

The upload key (`recipai_upload_keystore.jks`) is stored encrypted in the repository as
`docs/mobile/upload_key/recipai_upload_keystore.jks.gpg`.

## Decrypt the Keystore

```bash
cd mobile
gpg --decrypt recipai_upload_keystore.jks.gpg > recipai_upload_keystore.jks
```

You'll be prompted for the passphrase (stored in password manager).

## Encrypt the Keystore

If you need to update the encrypted version:

```bash
cd mobile
gpg --symmetric --cipher-algo AES256 upload-keystore.jks
```

## Configure for Build

Create `mobile/android/key.properties` with:

```properties
storePassword=<keystore-password>
keyPassword=<key-password-same-as-store-password>
keyAlias=upload
storeFile=<local-path-to-recipai-upload-keystore.jks>
```

**Important:** `android/key.properties` is gitignored and must be created locally after decrypting the keystore.

## Warning

Never commit the unencrypted `.jks` file or `key.properties` to Git.