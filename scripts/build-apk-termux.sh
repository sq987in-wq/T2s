#!/usr/bin/env bash
# ============================================================================
# T2S — one-command Android APK build for Termux (noob-friendly).
#
# What it does, in plain words:
#   1. Makes sure the project is cloned and on the fixed branch
#      `arena/01a04517-t2s` (you do NOT need to know what a branch is).
#   2. Installs the Java 17 + Gradle + SDK tools you need (only once).
#   3. Downloads the Android SDK bits (only once).
#   4. Builds a debug APK (the file you can install on your phone).
#
# Run it exactly like this inside Termux:
#     bash scripts/build-apk-termux.sh
#
# The finished APK is printed at the end — tap its path to install it.
#
# NOTE: This uses the pure-Kotlin engine (no NDK/C++), so the build needs
#       ONLY Java + the Android SDK — no NDK, no CMake, no C++ toolchain.
# ============================================================================
set -euo pipefail

REPO_URL="https://github.com/sq987in-wq/T2s.git"
BRANCH="arena/01a04517-t2s"
ANDROID_SDK_ROOT="$HOME/android-sdk"
CMDLINE_VERSION="11076708" # commandlinetools 12.0 (Java-based, runs on any arch)

echo "=================================================="
echo " T2S — Android APK builder (Termux)"
echo "=================================================="

# ----------------------------------------------------------------------------
# 1. Project + branch
# ----------------------------------------------------------------------------
if [ ! -d "$HOME/T2s/.git" ]; then
  echo ">> Cloning project..."
  git clone --branch "$BRANCH" "$REPO_URL" "$HOME/T2s"
else
  echo ">> Project already present; updating branch '$BRANCH'..."
  cd "$HOME/T2s"
  git fetch --all
  git checkout "$BRANCH" 2>/dev/null || git switch -C "$BRANCH" origin/"$BRANCH"
fi
cd "$HOME/T2s"

# ----------------------------------------------------------------------------
# 2. Java 17 + Gradle + helpers
# ----------------------------------------------------------------------------
echo ">> Installing Java 17, Gradle and helpers (first time only)..."
pkg install -y openjdk-17 gradle wget unzip || true

# ----------------------------------------------------------------------------
# 3. Android SDK
# ----------------------------------------------------------------------------
mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
if [ ! -x "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo ">> Downloading Android command-line tools..."
  cd /tmp
  wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip" -O cmdtools.zip
  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  unzip -q cmdtools.zip -d "$ANDROID_SDK_ROOT/cmdline-tools"
  mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
fi

export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

echo ">> Accepting SDK licenses and installing SDK 34 + build-tools..."
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null

# ----------------------------------------------------------------------------
# 4. Build
# ----------------------------------------------------------------------------
echo ">> Building debug APK (this can take a few minutes the first time)..."
# local.properties points Gradle at the SDK we just set up.
printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
gradle assembleDebug --no-daemon --stacktrace

echo ""
echo "=================================================="
echo " BUILD COMPLETE 🎉"
echo "=================================================="
APK=$(find app/build/outputs/apk/debug -name "*.apk" 2>/dev/null | head -1)
if [ -n "$APK" ]; then
  echo "Your app is ready to install:"
  echo "   $APK"
  echo "In Termux, run:  termux-open '$APK'"
  echo "(or find it in your file manager under android-sdk/../T2s/app/build/outputs/apk/debug)"
else
  echo "APK not found — build may have failed above. Scroll up for errors."
fi
echo "=================================================="
