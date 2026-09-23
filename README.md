# Lucense

A music player for **Android** and **Windows** that works on files you actually
own, identifies songs it doesn't recognise, and asks no service for an account.

It started as one workflow — *share a song from Telegram and it stays in your
library* — and grew from there. Nothing in it requires a login, an API key, or a
subscription; every service it talks to is one that answers without them.

---

## What it does

**Your library, your files.** On Android, share any audio file from Telegram (or
anything else) and it lands in the library permanently. On Windows, point it at a
folder and it reads what's there — your files are never copied or moved.

**Identify anything.** Four ways in:

| | how |
|---|---|
| **Name** | seven catalogues queried at once — iTunes, Deezer, MusicBrainz, Audius, the Internet Archive, YouTube, Genius |
| **Lyrics** | a half-remembered line, matched against Genius's and NetEase's lyric indexes |
| **A file** | audio *or video* — fingerprinted locally, only frequency peaks leave the machine |
| **A link** | TikTok, Instagram, YouTube and a thousand more — the audio is fetched, fingerprinted, and thrown away |

**Lyrics** come from four services in turn (LRCLIB, NetEase, lyrics.ovh, Genius),
keeping timed LRC where it exists so they scroll in sync. If all four come up
empty, you can paste them in yourself.

**Moods** matches songs to the weather — not "what you played while it rained",
which just returns your most-played songs, but what you play *disproportionately*
when it rains. See [`WeatherAffinity`](shared/src/main/kotlin/com/exo/musicplayer/data/weather/WeatherAffinity.kt).

**Downloads** from YouTube, SoundCloud, Bandcamp, Spotify and anywhere else
yt-dlp reaches, at your choice of quality.

**Also:** playlists you can export as text and swap with friends, listening
stats, duplicate detection, cover-art and tag repair in bulk, speed/pitch/reverb
that stack, simultaneous output to several devices, and a global hotkey that
ducks the music while you're gaming.

---

## Two apps, one brain

```
shared/    pure JVM, no Android — providers, fingerprinting, scoring, parsing
app/       Android    (Jetpack Compose, Media3/ExoPlayer, Room)
desktop/   Windows    (Compose Multiplatform, Java Sound, SQLite over JDBC)
```

Everything that decides *what a song is* lives in `shared/`, so the two platforms
cannot drift apart on it: the same relevance scorer, the same duplicate
heuristic, the same Shazam fingerprinter, the same weather maths.

---

## Some decisions worth explaining

**Why the Android app copies shared files.** Telegram's share intent carries a
`content://` URI backed by a *temporary* read grant — scoped to the receiving
task, revoked when it goes away, and impossible to make persistent
(`takePersistableUriPermission()` only works for `ACTION_OPEN_DOCUMENT`, not for
a share). A player that stores the URI has a library of dead links within
minutes. Lucense reads the bytes while the grant is live and writes its own
copy, which is why
[`ShareReceiverActivity`](app/src/main/java/com/exo/musicplayer/share/ShareReceiverActivity.kt)
shows a progress card and doesn't finish until the copy completes.

**Search results are ranked, not merged.** Seven services answer independently
and none can see the others, so merging them untouched put *"Hello Darkness My
Old Friend"* second for a search for *"nirvana come as you are"*.
[`MatchRanker`](shared/src/main/kotlin/com/exo/musicplayer/data/recognition/MatchRanker.kt)
scores every result on coverage, contiguity and whether you named the artist,
credits each query word against whichever field it best matches (so word order
doesn't matter), forgives typos within one edit for short words and two for long
ones, and drops what can't account for what you typed.

**Multi-device output decodes once.** Playing to several devices runs one decode
and writes the same PCM buffer to every open line, so they're sample-identical by
construction rather than by resynchronisation. Secondary devices get whatever
they can take and are dropped if they stall, so an unplugged speaker can never
block the one you're listening to.

**The duck hotkey uses `RegisterHotKey`, not a keyboard hook.** A
`WH_KEYBOARD_LL` hook would see every keystroke typed anywhere on the machine —
more than this needs, and indistinguishable from a keylogger to antivirus
software. `RegisterHotKey` forwards one specific key and nothing else. The cost
is that Windows reports the press but never the release, so ducking toggles
rather than being held.

**Nothing here breaks DRM.** Apple Music, Deezer and NetEase results are shown
for names, years and artwork, but carry no download button: their audio is
encrypted and there is no file to fetch. Spotify links go through
[spotdl](https://github.com/spotDL/spotify-downloader), which reads Spotify's
metadata and fetches the matching recording *from YouTube* — the result is a
match, not the Spotify file, and the UI says so rather than letting you find out
by listening.

---

## Building

Requires a JDK 17+ and, for Android, the SDK (`tools/fetch-deps.sh` will vendor
one if you'd rather not install it).

```bash
# Once, to fetch the tools the Windows build bundles (~200 MB).
bash tools/fetch-tools.sh

# Windows app — runnable image, then an installer
bash tools/build.sh :desktop:createDistributable
bash tools/build.sh :desktop:packageMsi

# Android
bash tools/build.sh :app:assembleDebug
```

`tools/build.sh` wraps Gradle with per-host proxy routing, which matters on
connections where some Maven mirrors are reachable and others aren't.

### Bundled tools

The Windows build ships three binaries so downloading works the moment it's
installed, rather than after a first-run setup step. They are fetched, not
committed — ffmpeg alone is 139 MB, past GitHub's file limit, and all three move
faster than this project does.

| tool | licence | used for |
|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense | downloading |
| [ffmpeg](https://github.com/yt-dlp/FFmpeg-Builds) | GPL-3.0 | MP3 conversion, audio out of video |
| [spotdl](https://github.com/spotDL/spotify-downloader) | MIT | Spotify links |

The Windows installer redistributes an unmodified upstream **GPL** ffmpeg build.
If you redistribute the installer, that binary carries GPL-3.0 terms with it —
the source is at the link above.

---

## Layout

```
shared/src/main/kotlin/com/exo/musicplayer/
  data/recognition/   providers, Shazam fingerprinting, relevance scoring
  data/lyrics/        four lyric services, LRC parsing
  data/weather/       Open-Meteo, affinity scoring
  data/playlist/      the shareable text format
  data/library/       duplicate matching
app/src/main/java/com/exo/musicplayer/
  share/              the Telegram receive path
  playback/           ExoPlayer service, audio focus, effects, output routing
desktop/src/main/kotlin/com/exo/musicplayer/desktop/
  audio/              decode, effects, multi-device engine
  data/               SQLite store, cover cache, controller
  ui/                 the Windows shell
```

---

## Contact

Telegram: [@Eth4wn](https://t.me/Eth4wn) — bugs, requests, or just to say it
broke on your machine.

---

## Licence

MIT, see [LICENSE](LICENSE). The bundled tools keep their own licences, listed
above.

Made by lucent.
