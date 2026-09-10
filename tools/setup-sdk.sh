#!/bin/bash
# Lays out a minimal Android SDK from the archives fetched by fetch-deps.sh, and
# unpacks the Gradle distribution.
#
# We skip Google's 136 MB commandlinetools bundle entirely: sdkmanager's only
# jobs here would be downloading these same packages and writing the license
# files, and we can do both directly for less than half the bytes.
set -eu
cd "$(dirname "$0")/.."

SDK="$PWD/.android-sdk"
DIST="$PWD/.gradle-dist"

need() {
  if [ ! -f "$1" ]; then
    echo "missing $1 -- run tools/fetch-deps.sh first" >&2
    exit 1
  fi
}
need "$SDK/platform-tools-latest-windows.zip"
need "$SDK/platform-35_r02.zip"
need "$SDK/build-tools_r35.0.1_windows.zip"
need "$DIST/gradle-8.9-bin.zip"

# Each SDK archive contains exactly one top-level directory whose name is the
# codename at release time ("android-15"), not the path the SDK expects.
extract_single_dir() { # zip staging_dir final_path
  local zip="$1" staging="$2" final="$3"
  if [ -d "$final" ]; then
    echo "have  $(basename "$final")"
    return 0
  fi
  echo "unzip $(basename "$zip")"
  rm -rf "$staging"
  mkdir -p "$staging"
  unzip -q "$zip" -d "$staging"
  local inner
  inner=$(find "$staging" -mindepth 1 -maxdepth 1 -type d | head -1)
  mkdir -p "$(dirname "$final")"
  mv "$inner" "$final"
  rm -rf "$staging"
  echo "ok    $final"
}

if [ ! -d "$SDK/platform-tools" ]; then
  echo "unzip platform-tools"
  unzip -q "$SDK/platform-tools-latest-windows.zip" -d "$SDK"
fi

extract_single_dir "$SDK/platform-35_r02.zip"          "$SDK/.stage-platform" "$SDK/platforms/android-35"
extract_single_dir "$SDK/build-tools_r35.0.1_windows.zip" "$SDK/.stage-bt"    "$SDK/build-tools/35.0.1"

# Records acceptance of the Android SDK Terms and Conditions
# (https://developer.android.com/studio/terms). These are the same hashes
# `sdkmanager --licenses` writes once you accept at its prompt; AGP refuses to
# build without them.
mkdir -p "$SDK/licenses"
printf '\n8933bad161af4178b1185d1a37fbf41ea5269c55\nd56f5187479451eabf01fb78af6dfcb131a6481e\n24333f8a63b6825ea9c5514f83c2829b004d1fee\n' \
  > "$SDK/licenses/android-sdk-license"
printf '\n84831b9409646a918e30573bab4c9c91346d8abd\n' \
  > "$SDK/licenses/android-sdk-preview-license"

if [ ! -d "$DIST/gradle-8.9" ]; then
  echo "unzip gradle"
  unzip -q "$DIST/gradle-8.9-bin.zip" -d "$DIST"
fi

# AGP reads the SDK location from here. Forward slashes are valid on Windows.
printf 'sdk.dir=%s\n' "$(echo "$SDK" | sed 's#^/\([a-zA-Z]\)/#\1:/#')" > local.properties

echo
echo "=== SDK READY ==="
echo "sdk.dir     $(cat local.properties)"
echo "platform    $(ls "$SDK/platforms")"
echo "build-tools $(ls "$SDK/build-tools")"
echo "gradle      $DIST/gradle-8.9/bin/gradle"
