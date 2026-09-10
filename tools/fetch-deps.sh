#!/bin/bash
# Fetches the Gradle distribution and the Android SDK packages this project needs.
#
# Two quirks of this machine are baked in:
#   1. dl.google.com returns HTTP 404 for developer downloads when reached
#      directly from here, so Google traffic goes through the local proxy
#      (HTTP_PROXY / HTTPS_PROXY, already set in the environment).
#   2. services.gradle.org is several times faster with the proxy bypassed.
#
# Transfers run strictly one at a time: the uplink is the bottleneck, and
# parallel transfers just split it. Every archive is verified against the
# publisher's own checksum -- Gradle's published .sha256, and the SHA-1s in
# Google's repository2-1.xml manifest -- so a truncated or tampered download is
# caught rather than unpacked. Downloads resume, so re-run after an interruption.
set -u
set -f  # no globbing: --noproxy '*' must survive as a literal asterisk

cd "$(dirname "$0")/.."
BASE="https://dl.google.com/android/repository"
COMMON="-sSL --retry 8 --retry-all-errors --retry-delay 5 -C - -m 7200"

digest() { # file algo -> hex
  case "$2" in
    sha1)   sha1sum   "$1" | awk '{print $1}' ;;
    sha256) sha256sum "$1" | awk '{print $1}' ;;
  esac
}

fetch() { # out_path url expected_bytes algo expected_digest [direct]
  local out="$1" url="$2" want="$3" algo="$4" sum="$5" direct="${6:-}"

  if [ -f "$out" ] && [ "$(stat -c %s "$out")" = "$want" ] && [ "$(digest "$out" "$algo")" = "$sum" ]; then
    echo "have  $(basename "$out")  (verified)"
    return 0
  fi

  mkdir -p "$(dirname "$out")"
  local have=0
  [ -f "$out" ] && have=$(stat -c %s "$out")
  echo "get   $(basename "$out")  ($((want / 1024 / 1024)) MB, from $((have / 1024 / 1024)) MB)"

  if [ -n "$direct" ]; then
    curl --noproxy '*' $COMMON -o "$out" "$url"
  else
    curl $COMMON -o "$out" "$url"
  fi

  local got_size got_sum
  got_size=$(stat -c %s "$out" 2>/dev/null || echo 0)
  if [ "$got_size" != "$want" ]; then
    echo "FAIL  $(basename "$out"): $got_size bytes, expected $want"
    return 1
  fi
  got_sum=$(digest "$out" "$algo")
  if [ "$got_sum" != "$sum" ]; then
    echo "FAIL  $(basename "$out"): $algo mismatch"
    echo "        got  $got_sum"
    echo "        want $sum"
    return 1
  fi
  echo "ok    $(basename "$out")  ($algo verified)"
}

fetch ".gradle-dist/gradle-8.9-bin.zip" \
      "https://services.gradle.org/distributions/gradle-8.9-bin.zip" \
      136114148 sha256 \
      d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab \
      direct || exit 1

fetch ".android-sdk/platform-35_r02.zip" \
      "$BASE/platform-35_r02.zip" \
      64273788 sha1 \
      0bb560a90a7a2cbd0dd8348224d518b638fe7949 || exit 1

fetch ".android-sdk/build-tools_r35.0.1_windows.zip" \
      "$BASE/build-tools_r35.0.1_windows.zip" \
      59876477 sha1 \
      1cbfa5564b62504a1111352edda35914cf723a20 || exit 1

# platform-tools ships only adb/fastboot and is versioned separately from the
# manifest above, so it gets a structural check rather than a pinned checksum.
PT=".android-sdk/platform-tools-latest-windows.zip"
if [ ! -f "$PT" ] || ! unzip -tq "$PT" > /dev/null 2>&1; then
  echo "get   platform-tools"
  curl $COMMON -o "$PT" "$BASE/platform-tools-latest-windows.zip"
  unzip -tq "$PT" > /dev/null 2>&1 || { echo "FAIL  platform-tools: corrupt archive"; exit 1; }
fi
echo "ok    platform-tools-latest-windows.zip  (archive intact)"

echo "=== ALL DEPENDENCIES DOWNLOADED ==="
