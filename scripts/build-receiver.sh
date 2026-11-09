#!/usr/bin/env bash
# Builds the Android Auto receiver (our Open Headunit fork, AGPL, kept OUT of this repo) and copies the
# debug APK to build/receiver/aa-receiver-debug.apk, where install-all.sh picks it up.
#   PHX_RECEIVER_DIR  fork checkout (default ../open-headunit, branch `phoenix`)
#   extra args are passed to Gradle (e.g. --offline)
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
REPO_ROOT="$(pwd)"
RECEIVER_DIR="${PHX_RECEIVER_DIR:-$REPO_ROOT/../open-headunit}"

if [ ! -f "$RECEIVER_DIR/gradlew" ]; then
  echo "Receiver fork not found at $RECEIVER_DIR" >&2
  echo "Clone it: git clone -b phoenix https://github.com/swdimock/open-headunit \"$RECEIVER_DIR\"" >&2
  echo "  (optional) git -C \"$RECEIVER_DIR\" remote add upstream https://github.com/andreknieriem/open-headunit" >&2
  exit 1
fi

# The fork needs to know where our user-scope SDK is (NDK 29.0.14206865 + CMake 3.22.1 live there too).
if [ ! -f "$RECEIVER_DIR/local.properties" ]; then
  cp "$REPO_ROOT/local.properties" "$RECEIVER_DIR/local.properties"
fi

echo "Building receiver in $RECEIVER_DIR (branch $(git -C "$RECEIVER_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo '?'))"
(cd "$RECEIVER_DIR" && ./gradlew :app:assembleGithubDebug "$@")

APK="$(ls -t "$RECEIVER_DIR"/app/build/outputs/apk/github/debug/*.apk | head -1)"
mkdir -p build/receiver
cp "$APK" build/receiver/aa-receiver-debug.apk
echo "Receiver APK: build/receiver/aa-receiver-debug.apk (from $(basename "$APK"))"
