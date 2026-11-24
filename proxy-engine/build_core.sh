#!/usr/bin/env bash
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "$0")" && pwd)"
CORE_DIR="$MODULE_DIR/native/libcore"
OUTPUT_AAR="${1:?usage: build_core.sh <output-aar>}"
SING_BOX_COMMIT="aed32ee3066cdbc7d471e3e0415c5134088962df"
LIBNEKO_COMMIT="1c47a3af71990a7b2192e03292b4d246c308ef0b"
ANDROID_HOME="${ANDROID_HOME:-}"

if ! command -v go >/dev/null 2>&1; then
  echo "NekoBox proxy core needs Go 1.24 or newer on PATH." >&2
  exit 1
fi

# The gomobile fork requires Go 1.24. Newer Go releases removed GODEBUG values
# still present in its go.mod, so select its compatible minimum toolchain.
export GOTOOLCHAIN=go1.24.0

if [ -z "$ANDROID_HOME" ]; then
  if [ -n "${ANDROID_SDK_ROOT:-}" ]; then
    ANDROID_HOME="$ANDROID_SDK_ROOT"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"
  elif [ -d "$HOME/Android/Sdk" ]; then
    ANDROID_HOME="$HOME/Android/Sdk"
  fi
fi

ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-}"
if [ -z "$ANDROID_NDK_HOME" ] && [ -n "$ANDROID_HOME" ]; then
  if [ -d "$ANDROID_HOME/ndk/28.2.13676358" ]; then
    ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
  elif [ -d "$ANDROID_HOME/ndk" ]; then
    ANDROID_NDK_HOME="$(find "$ANDROID_HOME/ndk" -mindepth 1 -maxdepth 1 -type d | sort | tail -n 1)"
  fi
fi
if [ -z "$ANDROID_HOME" ] || [ -z "$ANDROID_NDK_HOME" ] || [ ! -f "$ANDROID_NDK_HOME/source.properties" ]; then
  echo "Android SDK and NDK are required to build NekoBox's mobile core." >&2
  exit 1
fi
export ANDROID_HOME ANDROID_NDK_HOME
export NDK="$ANDROID_NDK_HOME"

ensure_checkout() {
  local url="$1"
  local directory="$2"
  local commit="$3"
  if [ ! -d "$directory/.git" ]; then
    rm -rf "$directory"
    mkdir -p "$directory"
    git -C "$directory" init
    git -C "$directory" remote add origin "$url"
  fi
  git -C "$directory" fetch --depth=1 origin "$commit"
  git -C "$directory" checkout --detach FETCH_HEAD
}

ensure_checkout "https://github.com/MatsuriDayo/sing-box.git" "$MODULE_DIR/sing-box" "$SING_BOX_COMMIT"
ensure_checkout "https://github.com/MatsuriDayo/libneko.git" "$MODULE_DIR/libneko" "$LIBNEKO_COMMIT"

GOPATH="${GOPATH:-$(go env GOPATH)}"
export GOPATH
export PATH="$GOPATH/bin:$PATH"

if [ ! -x "$GOPATH/bin/gomobile-matsuri" ] || [ ! -x "$GOPATH/bin/gobind-matsuri" ]; then
  GOMOBILE_SOURCE="$MODULE_DIR/.cache/gomobile-matsuri"
  if [ ! -d "$GOMOBILE_SOURCE/.git" ]; then
    rm -rf "$GOMOBILE_SOURCE"
    git clone --depth=1 --branch master2 https://github.com/MatsuriDayo/gomobile.git "$GOMOBILE_SOURCE"
  fi
  (
    cd "$GOMOBILE_SOURCE/cmd/gomobile"
    go install
  )
  (
    cd "$GOMOBILE_SOURCE/cmd/gobind"
    go install
  )
  mv -f "$GOPATH/bin/gomobile" "$GOPATH/bin/gomobile-matsuri"
  mv -f "$GOPATH/bin/gobind" "$GOPATH/bin/gobind-matsuri"
fi

mkdir -p "$(dirname "$OUTPUT_AAR")" "$CORE_DIR/.build"
cd "$CORE_DIR"
export GOBIND=gobind-matsuri
"$GOPATH/bin/gomobile-matsuri" bind \
  -v \
  -target=android/arm,android/arm64 \
  -androidapi 24 \
  -cache "$(realpath .build)" \
  -trimpath \
  -ldflags='-s -w' \
  -tags='with_conntrack,with_gvisor,with_quic,with_wireguard,with_utls,with_clash_api' \
  -o "$OUTPUT_AAR" \
  .
rm -f libcore-sources.jar
