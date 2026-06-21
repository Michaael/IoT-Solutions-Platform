#!/bin/bash
set -euo pipefail

STAGING_DIR="${1:-}"
if [ -z "$STAGING_DIR" ] || [ ! -d "$STAGING_DIR" ]; then
  echo "Usage: $0 /opt/ispf/staging/<version>" >&2
  exit 1
fi

JAR_PATH="$STAGING_DIR/ispf-server.jar"
UI_ZIP="$STAGING_DIR/web-console.zip"
INSTALL_ROOT="${ISPF_INSTALL_ROOT:-/opt/ispf}"
SERVICE_NAME="${ISPF_SERVICE_NAME:-ispf-server}"

if [ ! -f "$JAR_PATH" ] || [ ! -f "$UI_ZIP" ]; then
  echo "Missing release artifacts in $STAGING_DIR" >&2
  exit 1
fi

echo "=== ISPF platform update from $STAGING_DIR ==="
sleep 3

systemctl stop "$SERVICE_NAME"

install -d "$INSTALL_ROOT/data" "$INSTALL_ROOT/web-console"
install -m 644 "$JAR_PATH" "$INSTALL_ROOT/ispf-server.jar"

TMP_UI="$(mktemp -d)"
trap 'rm -rf "$TMP_UI"' EXIT
unzip -oq "$UI_ZIP" -d "$TMP_UI"
rm -rf "$INSTALL_ROOT/web-console"/*
if [ -d "$TMP_UI/dist" ]; then
  cp -a "$TMP_UI/dist"/. "$INSTALL_ROOT/web-console/"
else
  cp -a "$TMP_UI"/. "$INSTALL_ROOT/web-console/"
fi
chmod -R a+rX "$INSTALL_ROOT/web-console"
find "$INSTALL_ROOT/web-console" -type d -exec chmod 755 {} +

systemctl start "$SERVICE_NAME"
echo "=== Update complete ==="
