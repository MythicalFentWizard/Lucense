#!/usr/bin/env bash
#
# Fetches the command-line tools the desktop build bundles.
#
# These are not committed. ffmpeg alone is 139 MB, comfortably past GitHub's
# 100 MB per-file limit, and all of them are unmodified upstream releases that
# are better linked than vendored — not least because yt-dlp ships fixes
# whenever a site changes its player, which is far more often than this
# project releases.
#
# Run once after cloning, before building the desktop app:
#
#     bash tools/fetch-tools.sh          # Windows: desktop/resources/windows-x64/
#     bash tools/fetch-tools.sh linux    # Linux:   desktop/resources/linux-x64/
#
# The Compose plugin's appResourcesRootDir packages the folder for the system
# being built on.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLATFORM="${1:-windows}"

# spotdl is pinned because its CLI flags have changed between majors and the
# downloader passes --bitrate and --output templates. yt-dlp and ffmpeg track
# latest on purpose: both are expected to move faster than this project.
SPOTDL_VERSION="4.5.2"

case "$PLATFORM" in
  windows)
    DEST="$ROOT/desktop/resources/windows-x64"
    EXT=".exe"
    YTDLP_URL="https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
    FFMPEG_URL="https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip"
    QJS_URL="https://github.com/quickjs-ng/quickjs/releases/latest/download/qjs-windows-x86_64.exe"
    SPOTDL_URL="https://github.com/spotDL/spotify-downloader/releases/download/v${SPOTDL_VERSION}/spotdl-${SPOTDL_VERSION}-win32.exe"
    ;;
  linux)
    DEST="$ROOT/desktop/resources/linux-x64"
    EXT=""
    # The standalone build, which carries its own Python: nothing to install.
    YTDLP_URL="https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux"
    FFMPEG_URL="https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-linux64-gpl.tar.xz"
    QJS_URL="https://github.com/quickjs-ng/quickjs/releases/latest/download/qjs-linux-x86_64"
    SPOTDL_URL="https://github.com/spotDL/spotify-downloader/releases/download/v${SPOTDL_VERSION}/spotdl-${SPOTDL_VERSION}-linux"
    ;;
  *)
    echo "usage: $0 [windows|linux]" >&2
    exit 2
    ;;
esac
mkdir -p "$DEST"

# curl honours http_proxy/https_proxy from the environment, which is how this
# reaches GitHub on connections that need one.
fetch() {
  local url="$1" out="$2" name="$3"
  if [ -s "$out" ]; then
    printf '  %-12s already present (%s)\n' "$name" "$(du -h "$out" | cut -f1)"
    return
  fi
  printf '  %-12s downloading...\n' "$name"
  curl -fsSL --retry 3 --retry-delay 2 -o "$out.part" "$url"
  mv "$out.part" "$out"
  chmod +x "$out"
  printf '  %-12s done (%s)\n' "$name" "$(du -h "$out" | cut -f1)"
}

echo "Fetching bundled tools into ${DEST#$ROOT/}/"

fetch "$YTDLP_URL" "$DEST/yt-dlp$EXT" "yt-dlp"
# yt-dlp solves YouTube's JavaScript challenges in an engine beside it, and calls
# running without one deprecated. It looks for Deno by default (100 MB);
# QuickJS-NG is supported too and is 2 MB.
fetch "$QJS_URL" "$DEST/qjs$EXT" "quickjs"
fetch "$SPOTDL_URL" "$DEST/spotdl$EXT" "spotdl"

if [ -s "$DEST/ffmpeg$EXT" ]; then
  printf '  %-12s already present (%s)\n' "ffmpeg" "$(du -h "$DEST/ffmpeg$EXT" | cut -f1)"
else
  printf '  %-12s downloading archive...\n' "ffmpeg"
  ARCHIVE="$DEST/ffmpeg-archive"
  curl -fsSL --retry 3 --retry-delay 2 -o "$ARCHIVE" "$FFMPEG_URL"
  # The archive nests everything under a versioned directory, so the entry is
  # matched by name rather than by full path.
  PYTHON="$(command -v python3 || command -v python)"
  "$PYTHON" - "$ARCHIVE" "$DEST" "ffmpeg$EXT" <<'PY'
import os, sys, tarfile, zipfile
archive, dest, wanted = sys.argv[1], sys.argv[2], sys.argv[3]
out = os.path.join(dest, wanted)
if zipfile.is_zipfile(archive):
    with zipfile.ZipFile(archive) as zf:
        entry = next((e for e in zf.namelist() if e.rsplit("/", 1)[-1] == wanted), None)
        if entry is None:
            raise SystemExit(wanted + " was not in the archive")
        with zf.open(entry) as src, open(out, "wb") as dst:
            dst.write(src.read())
else:
    with tarfile.open(archive) as tf:
        entry = next((m for m in tf.getmembers() if m.isfile() and m.name.rsplit("/", 1)[-1] == wanted), None)
        if entry is None:
            raise SystemExit(wanted + " was not in the archive")
        with tf.extractfile(entry) as src, open(out, "wb") as dst:
            dst.write(src.read())
os.chmod(out, 0o755)
PY
  rm -f "$ARCHIVE"
  printf '  %-12s done (%s)\n' "ffmpeg" "$(du -h "$DEST/ffmpeg$EXT" | cut -f1)"
fi

echo
echo "Done. Licences of the fetched binaries:"
echo "  yt-dlp   Unlicense          https://github.com/yt-dlp/yt-dlp"
echo "  ffmpeg   GPL-3.0 (gpl build) https://github.com/yt-dlp/FFmpeg-Builds"
echo "  spotdl   MIT                https://github.com/spotDL/spotify-downloader"
echo "  quickjs  MIT                https://github.com/quickjs-ng/quickjs"
