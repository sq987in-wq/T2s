#!/usr/bin/env bash
# ============================================================================
# T2S — apply the NATIVE espeak-ng overhaul and rebuild the APK (Termux).
#
# This script fetches the latest code (which now has a "native-first" engine:
# it uses real espeak-ng when libpiper_phonemize.so is present, otherwise the
# proven pure-Kotlin G2P), and rebuilds the APK.
#
# IMPORTANT HONESTY NOTE:
#   The native .so is cross-compiled for ARM64 with the Android NDK, which
#   Termux cannot realistically do (it needs the full NDK toolchain + building
#   espeak-ng). So on a phone this script builds the PURE-KOTLIN engine, which
#   is now PROVEN correct (all letters preserved, schwa deletion, anusvara,
#   piper-exact padding — verified by CI unit tests).
#   To get true byte-parity audio, build the .so on a Linux/CI machine with
#   scripts/build-native-lib.sh, drop the .so into app/src/main/jniLibs/arm64-v8a/
#   (+ bundle espeak-ng-data), then this script builds the APK with native on.
# ============================================================================
set -euo pipefail

BRANCH="arena/01a04517-t2s"
REPO_URL="https://github.com/sq987in-wq/T2s.git"
ANDROID_SDK_ROOT="$HOME/android-sdk"

echo "=================================================="
echo " T2S — native-first engine + APK rebuild"
echo "=================================================="

cd "$HOME/T2s" 2>/dev/null || { echo ">> Cloning project..."; git clone --branch "$BRANCH" "$REPO_URL" "$HOME/T2s"; cd "$HOME/T2s"; }

echo ">> Fetching latest code..."
git fetch origin "$BRANCH"
git checkout "$BRANCH" 2>/dev/null || git switch -C "$BRANCH" origin/"$BRANCH"
git merge --ff-only origin/"$BRANCH" 2>/dev/null || git reset --hard origin/"$BRANCH"

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties

# If a prebuilt native .so is present, enable the native build; else Kotlin-only.
if [ -f app/src/main/jniLibs/arm64-v8a/libpiper_phonemize.so ]; then
  echo ">> Native lib found — building with espeak-ng (byte-parity) enabled."
  gradle assembleDebug -Pt2s.native=true --no-daemon --stacktrace
else
  echo ">> No native .so present — building the proven pure-Kotlin engine."
  echo "   (For byte-parity, build libpiper_phonemize.so on Linux/CI first.)"
  gradle assembleDebug --no-daemon --stacktrace
fi

APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
echo ""
echo "=================================================="
echo " BUILD COMPLETE 🎉"
echo "=================================================="
if [ -n "$APK" ]; then
  echo "Install this file (replaces the old app):"
  echo "   $APK"
  echo "In Termux, run:  termux-open '$APK'"
else
  echo "APK not found — scroll up to see if the build failed."
fi
echo "=================================================="
