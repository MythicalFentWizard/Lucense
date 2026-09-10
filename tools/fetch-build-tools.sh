#!/bin/bash
# Downloads build-tools 35.0.1 only, verified against Google's published SHA-1.
set -u
set -f
cd "$(dirname "$0")/.."
OUT=".android-sdk/build-tools_r35.0.1_windows.zip"
URL="https://dl.google.com/android/repository/build-tools_r35.0.1_windows.zip"
WANT_SIZE=59876477
WANT_SHA1=1cbfa5564b62504a1111352edda35914cf723a20

for attempt in $(seq 1 40); do
  have=0; [ -f "$OUT" ] && have=$(stat -c %s "$OUT")
  if [ "$have" = "$WANT_SIZE" ]; then
    got=$(sha1sum "$OUT" | awk '{print $1}')
    if [ "$got" = "$WANT_SHA1" ]; then echo "OK build-tools verified (sha1 $got)"; exit 0; fi
    echo "sha1 mismatch, restarting from scratch"; rm -f "$OUT"
  fi
  echo "attempt $attempt: resuming from $((have/1024/1024)) MB of 57 MB"
  # Google is only reachable through the local proxy from this machine.
  curl -sSL --retry 5 --retry-all-errors --retry-delay 5 -C - -m 3600 -o "$OUT" "$URL" || true
done
echo "FAIL build-tools: gave up after 40 attempts"; exit 1
