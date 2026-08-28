#!/usr/bin/env bash
# ============================================================================
# Build libpiper_phonemize.so (espeak-ng) for Android ARM64 — NATIVE BYTE-PARITY.
#
# Run this on a machine with the Android NDK + CMake + git (Linux/macOS, or a
# GitHub Actions runner). This is NOT a Termux phone build — cross-compiling
# piper-phonemize + espeak-ng needs the full NDK toolchain.
#
# Prereqs (set them or export them):
#   ANDROID_NDK   path to the Android NDK (e.g. ~/android-ndk-r25b)
#   ANDROID_PLATFORM (default 28 = minSdk)
#
# Usage:
#   ANDROID_NDK=$HOME/android-ndk-r25b bash scripts/build-native-lib.sh
#
# Output: app/src/main/jniLibs/arm64-v8a/libpiper_phonemize.so
#         app/src/main/jniLibs/x86_64/libpiper_phonemize.so
#
# NOTE: Before this will load at runtime you must ALSO bundle espeak-ng-data
# (piper-phonemize ships a prebuilt one) and pass its extracted path to
# NativePhonemizer(espeakDataPath=...). See docs/native-reference/.
# ============================================================================
set -euo pipefail

cd "$(dirname "$0")/.."   # repo root
ROOT="$(pwd)"
NDK="${ANDROID_NDK:?Set ANDROID_NDK to your NDK path}"
PLATFORM="${ANDROID_PLATFORM:-28}"
BUILD_DIR="$ROOT/app/.native-build"
SRC="$ROOT/app/src/main/cpp"

command -v cmake >/dev/null || { echo "cmake not found"; exit 1; }
command -v git   >/dev/null || { echo "git not found"; exit 1; }

for ABI in arm64-v8a x86_64; do
  echo ">> Building $ABI ..."
  cmake -S "$SRC" -B "$BUILD_DIR/$ABI" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI="$ABI" \
        -DANDROID_PLATFORM="$PLATFORM" \
        -DCMAKE_BUILD_TYPE=Release
  cmake --build "$BUILD_DIR/$ABI"
  OUT_DIR="$ROOT/app/src/main/jniLibs/$ABI"
  mkdir -p "$OUT_DIR"
  cp "$BUILD_DIR/$ABI/libpiper_phonemize.so" "$OUT_DIR/"
  echo ">> Wrote $OUT_DIR/libpiper_phonemize.so"
done

echo ""
echo "Native lib built. Now build the APK with the native path ENABLED:"
echo "   gradle assembleRelease -Pt2s.native=true"
echo ""
echo "Then bundle espeak-ng-data into app assets and set"
echo "NativePhonemizer(espeakDataPath = <extracted dir>) in the app."
