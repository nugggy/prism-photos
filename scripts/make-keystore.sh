#!/usr/bin/env bash
# Creates a release keystore for signing Prism and prints the values to put in
# GitHub Actions secrets. Run once, keep the .jks somewhere safe, never commit it.
#
#   bash scripts/make-keystore.sh
#
set -euo pipefail

OUT="${1:-prism-release.jks}"
ALIAS="${2:-prism}"

if [ -f "$OUT" ]; then
  echo "$OUT already exists. Refusing to overwrite." >&2
  exit 1
fi

read -r -s -p "Keystore password: " STORE_PASS; echo
read -r -s -p "Key password (Enter to reuse the keystore password): " KEY_PASS; echo
KEY_PASS="${KEY_PASS:-$STORE_PASS}"

keytool -genkeypair -v \
  -keystore "$OUT" -storepass "$STORE_PASS" \
  -alias "$ALIAS" -keypass "$KEY_PASS" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Prism, OU=Prism, O=Prism, L=Sydney, ST=NSW, C=AU"

cat > keystore.properties <<EOF
storeFile=$(realpath "$OUT")
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$KEY_PASS
EOF

echo
echo "Wrote $OUT and keystore.properties (both are git ignored)."
echo
echo "GitHub repository secrets to add:"
echo "  PRISM_KEYSTORE_BASE64   = (contents of the line below)"
base64 -w 0 "$OUT" 2>/dev/null || base64 "$OUT" | tr -d '\n'
echo
echo "  PRISM_KEYSTORE_PASSWORD = the keystore password you typed"
echo "  PRISM_KEY_ALIAS         = $ALIAS"
echo "  PRISM_KEY_PASSWORD      = the key password you typed"
