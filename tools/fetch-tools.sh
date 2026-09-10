#!/usr/bin/env bash
#
# Fetches the command-line tools the Windows build bundles.
#
# These are not committed. ffmpeg.exe alone is 139 MB, comfortably past GitHub's
# 100 MB per-file limit, and all three are unmodified upstream releases that are
# better linked than vendored — not least because yt-dlp ships fixes whenever a
# site changes its player, which is far more often than this project releases.
#
# Run once after cloning, before building the desktop app:
#
#     bash tools/fetch-tools.sh
#
# Downloads land in desktop/resources/windows-x64/ and are packaged into the app
# image by the Compose plugin's appResourcesRootDir.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$ROOT/desktop/resources/windows-x64"
mkdir -p "$DEST"

# spotdl is pinned because its CLI flags have changed between majors and the
# downloader passes --bitrate and --output templates. yt-dlp and ffmpeg track
# latest on purpose: both are expected to move faster than this project.
SPOTDL_VERSION="4.5.2"

YTDLP_URL="https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
FFMPEG_URL="https://github.com/yt-dlp/FFmpeg-Builds/releases/latest/download/ffmpeg-master-latest-win64-gpl.zip"
SPOTDL_URL="https://github.com/spotDL/spotify-downloader/releases/download/v${SPOTDL_VERSION}/spotdl-${SPOTDL_VERSION}-win32.exe"

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
  printf '  %-12s done (%s)\n' "$name" "$(du -h "$out" | cut -f1)"
}

echo "Fetching bundled tools into desktop/resources/windows-x64/"

fetch "$YTDLP_URL" "$DEST/yt-dlp.exe" "yt-dlp"
fetch "$SPOTDL_URL" "$DEST/spotdl.exe" "spotdl"

if [ -s "$DEST/ffmpeg.exe" ]; then
  printf '  %-12s already present (%s)\n' "ffmpeg" "$(du -h "$DEST/ffmpeg.exe" | cut -f1)"
else
  printf '  %-12s downloading archive...\n' "ffmpeg"
  ZIP="$DEST/ffmpeg.zip"
  curl -fsSL --retry 3 --retry-delay 2 -o "$ZIP" "$FFMPEG_URL"
  # The archive nests everything under a versioned directory, so the entry is
  # matched by name rather than by full path.
  python - "$ZIP" "$DEST" <<'PY'
import sys, zipfile, os
archive, dest = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(archive) as zf:
    for entry in zf.namelist():
        if entry.rsplit("/", 1)[-1] == "ffmpeg.exe":
            with zf.open(entry) as src, open(os.path.join(dest, "ffmpeg.exe"), "wb") as out:
                out.write(src.read())
            break
    else:
        raise SystemExit("ffmpeg.exe was not in the archive")
PY
  rm -f "$ZIP"
  printf '  %-12s done (%s)\n' "ffmpeg" "$(du -h "$DEST/ffmpeg.exe" | cut -f1)"
fi

echo
echo "Done. Licences of the fetched binaries:"
echo "  yt-dlp   Unlicense          https://github.com/yt-dlp/yt-dlp"
echo "  ffmpeg   GPL-3.0 (gpl build) https://github.com/yt-dlp/FFmpeg-Builds"
echo "  spotdl   MIT                https://github.com/spotDL/spotify-downloader"
