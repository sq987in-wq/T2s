#!/usr/bin/env bash
# ============================================================================
# T2S — apply the "stuck on Processing phonemes" fix and rebuild the APK.
#
# Run this in Termux:
#     bash scripts/apply-fix-termux.sh
#
# What it does, in plain words:
#   1. Gets the latest fixed code onto branch arena/01a04517-t2s.
#   2. Rebuilds the debug APK.
#   3. Tells you exactly where the finished APK is.
#
# (If you haven't built before, run scripts/build-apk-termux.sh instead — it
#  also installs Java/SDK. This script is for when you already built once.)
# ============================================================================
set -euo pipefail

BRANCH="arena/01a04517-t2s"
REPO_URL="https://github.com/sq987in-wq/T2s.git"
ANDROID_SDK_ROOT="$HOME/android-sdk"

echo "=================================================="
echo " T2S — apply fix & rebuild"
echo "=================================================="

cd "$HOME/T2s" 2>/dev/null || { echo ">> Cloning project..."; git clone --branch "$BRANCH" "$REPO_URL" "$HOME/T2s"; cd "$HOME/T2s"; }

echo ">> Fetching the fixed code..."
git fetch origin "$BRANCH"
git checkout "$BRANCH" 2>/dev/null || git switch -C "$BRANCH" origin/"$BRANCH"
git merge --ff-only origin/"$BRANCH" 2>/dev/null || git reset --hard origin/"$BRANCH"

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties

echo ">> Rebuilding debug APK..."
gradle assembleDebug --no-daemon --stacktrace

APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
echo ""
echo "=================================================="
echo " FIX APPLIED ✅"
echo "=================================================="
if [ -n "$APK" ]; then
  echo "Install this file (replaces the old app):"
  echo "   $APK"
  echo "In Termux, run:  termux-open '$APK'"
else
  echo "APK not found — scroll up to see if the build failed."
fi
echo "=================================================="
