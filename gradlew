#!/bin/sh
# Minimal Gradle wrapper delegate (Option C — CI handles full compilation)
# This avoids local heavy downloads; GitHub Actions uses native gradle.
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    echo "Gradle not found locally. This repository uses GitHub Actions CI for builds." >&2
    echo "Run: ./gradlew build  (on CI only, or install Gradle locally)" >&2
    exit 1
fi
