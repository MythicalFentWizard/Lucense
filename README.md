# Lucense

A music player for **Android** and **Windows** that works on files you actually
own, identifies songs it doesn't recognise, and asks no service for an account.

It started as one workflow — *share a song from Telegram and it stays in your
library* — and grew from there. Nothing in it requires a login, an API key, or a
subscription; every service it talks to is one that answers without them.

**[Download the latest release →](https://github.com/MythicalFentWizard/Lucense/releases/latest)**

---

## Install

| | file | needs |
|---|---|---|
| **Windows** | `Lucense-<version>.msi` | Windows 10 or 11, 64-bit. Java, yt-dlp, ffmpeg and spotdl come inside it — nothing else to install. |
| **Android** | `Lucense-<version>.apk` | Android 8.0 or newer, on a 64-bit (arm64) phone. |

- **Windows:** the installer isn't code-signed, so SmartScreen may say *Windows
  protected your PC*. Press **More info → Run anyway**.
- **Windows upgrades:** a newer `.msi` upgrades the one you already have, and your
  library, playlists, ratings and play counts stay.
- **Android:** the APK is debug-signed, so Android asks you to allow installs from
  whichever app you opened it with.

---

## What it does

**Your library, your files.** On Android, share any audio file from Telegram (or
anything else) and it lands in the library permanently. On Windows, point it at a
folder and it reads what's there — your files are never copied or moved, and the
library keeps up with new, renamed and deleted files on its own.

**Identify anything.** Four ways in:

| | how |
|---|---|
| **Name** | seven catalogues queried at once — iTunes, Deezer, MusicBrainz, Audius, the Internet Archive, YouTube, Genius |
| **Lyrics** | a half-remembered line, matched against Genius's and NetEase's lyric indexes |
| **A file** | audio *or video* — fingerprinted locally, only frequency peaks leave the machine |
| **A link** | TikTok, Instagram, YouTube and a thousand more — the audio is fetched, fingerprinted, and thrown away |

**Lyrics** come from four services in turn (LRCLIB, NetEase, lyrics.ovh, Genius),
keeping timed LRC where it exists so they scroll in sync. If all four come up
empty, you can paste them in yourself. You choose what gets searched for: the tags
as they are, a tidied title with `(Official Video)`, `[HD]` and `feat. …`
stripped, the title alone, the file name, or your own pattern.

**Moods** matches songs to the weather — not "what you played while it rained",
which just returns your most-played songs, but what you play *disproportionately*
when it rains. See [`WeatherAffinity`](shared/src/main/kotlin/com/exo/musicplayer/data/weather/WeatherAffinity.kt).

**Downloads** from YouTube, SoundCloud, Bandcamp, Spotify and anywhere else
yt-dlp reaches, at your choice of quality.

### Playing

- **Gapless, with optional crossfade.** One song runs straight into the next with
  no silence. Settings → Playback → *Crossfade* overlaps them by 2 to 12 seconds.
- **Volume levelling.** *Level volumes* measures each song once, and after that
  they all play at the same loudness. Nothing is written to the files.
- **A volume slider in decibels.** Every part of the slider does something. It
  takes the mouse wheel, and clicking the speaker (or pressing **M**) mutes and
  unmutes.
- **A seek bar you can grab and drag.** The drag keeps following the mouse even
  after it leaves the bar.
- **Shuffle and repeat.** Shuffle doesn't interrupt the song that's playing.
  **Previous** restarts the song if you're more than four seconds in.
- **A queue you can edit.**
  - *Play next* and *Add to queue* are on every song.
  - The **Up next** panel shows the real order, shuffle included. From there you
    can move songs up and down, take them out, or clear the queue.
- **Resume where you left off**, even after closing the app.
- **Sleep timer:** 15 to 90 minutes, or *when this song ends*. It fades out over
  the last half minute instead of cutting off mid-bar.
- **Effects:** speed, pitch, reverb and EQ, all combinable, with a spectrum meter.
  *Remember for this song* brings a setting back whenever that song plays.
- **More than one output at once:** several devices play the same audio, exactly
  in step.
- **Game ducking:** a global hotkey turns the music down while you're gaming.
- **Mini player:** a small always-on-top window. There's also a tray icon with
  play/pause, next, previous and quit.

### Finding things

Plain words in the search box work as you'd expect, and accents are ignored, so
`proz` finds *Pröz*. The same box also takes filters:

```
artist:proz   album:wave   title:star   genre:rock   artist:"tyler, the creator"
fav:   unplayed:   rating:4+   stars:<3   year:2015-2020   plays:0   added:30d
```

Combine as many filters as you like. The last eight searches appear as chips
when the box is empty.

You can sort by artist, title, album, length, times played, time listened, date
added, rating or year. **J** scrolls the list to whatever is playing.

### Playlists

- **Your own playlists**, exportable as plain text you can swap with friends.
- **Made for you:** *Recently added*, *Recently played*, *Most played*, *Never
  played*, *Favourites* and *Top rated*. They're worked out from your library and
  listening, and nothing leaves the machine.
- **Smart lists.** A smart list is a rule written in the search grammar above, so
  it refills itself as the library changes. You see how many songs match while
  you type the rule.
- **Genre lists.** Type a genre and get a list. You can play it, save it, or keep
  it as a rule. Genres are read from the files, and a song can have several
  (`Hip-Hop/Rap`, `Rock; Alternative`).
- **Star ratings and favourites** on every song.

### Fixing a messy library (Windows)

**Bulk tools** works through the whole library. Songs a tool has already handled
are skipped unless you tick *Redo*.

| tool | what it does |
|---|---|
| **Names & tags** | looks songs up by name and writes the title and artist, or the album, year and genre, or both — it asks which |
| **Identify by sound** | fingerprints the audio, for files with no usable name at all |
| **Names from folders** | reads `Artist/Album/01 Title` out of the folder tree; fills blanks only |
| **Covers** | missing cover art from six sources, trying your preferred one first |
| **Lyrics** | fetches lyrics for everything, keeping timed ones where they exist |
| **Level volumes** | measures loudness so everything plays at the same level |
| **Mass fix Telegram songs** | rebuilds files that came without a proper header, so they show their real length and seek properly — the audio itself is untouched |

You can also select a batch of songs and set their artist, album, year or genre
all at once. **Duplicates** finds the same song stored twice.

When a tool guesses wrong, *Revert to the file's own details* puts back what the
file said before anything touched it.

### Around the app (Windows)

- **Keyboard shortcuts.** They work whenever you're not typing in a box, and
  **/** lists them all:

  | key | does |
  |---|---|
  | Space | play or pause |
  | ← → | back or forward five seconds |
  | Ctrl + ← → | previous or next song |
  | ↑ ↓ | volume |
  | J | jump the list to the song playing |
  | M | mute, or put the volume back |
  | S · R | shuffle · repeat (all, one, off) |
  | Ctrl + A · Esc | select everything in view · clear the selection |

  Your keyboard's or headset's **media keys** work even while Lucense is in the
  background.
- **Discord Rich Presence.** Your profile shows the song, the artist, the song's
  own cover with the Lucense badge on its corner, and a running timer. It clears
  when you pause. It talks to the Discord app on your PC, and there's nothing to
  sign in to.
- **Open with Lucense.** Settings → Playback can offer Lucense in Explorer's
  *Open with* menu for music files. Windows doesn't let an app make itself the
  default player, so that last step is yours.
- **Lyrics in their own window**, which can sit anywhere on screen.
- **Themes:**
  - Thirteen published colour schemes: Catppuccin Mocha and Latte, Dracula,
    Tokyo Night, Rosé Pine, Nord, Gruvbox, Everforest, Solarized dark and light,
    One Dark, Ayu Mirage and Daylight.
  - Every scheme is checked for readable contrast.
  - You can also build your own theme.
- **Backgrounds and wallpaper:** stars, aurora, fireflies, snow, a scrolling graph
  of the song, or visuals that move with the music. You can set your own picture
  behind them.
- **Backups.** Favourites, playlists, play counts and listening time go to a plain
  text file. Restoring adds them back and never deletes anything.
- **Stats** on what you listen to, and when.
- **Update check:** Lucense tells you in Settings when a newer build is out.

### On Android

- The Telegram share that started all this.
- The same identify, lyrics, moods, stats, playlists and downloads as Windows.
- Genres, smart lists (*Genres and rules* on the Playlists screen), and *revert to
  the file's own details*.
- Speed, pitch and reverb with a spectrum meter, Up next, shuffle and repeat.
- Wallpaper with a dim slider, the starfield, and your own lyric colours.

Some things are Windows-only for now: bulk tools, crossfade, ratings, the sleep
timer, Discord and the keyboard shortcuts.

---

## Two apps, one brain

```
shared/    pure JVM, no Android — providers, fingerprinting, scoring, parsing
app/       Android    (Jetpack Compose, Media3/ExoPlayer, Room)
desktop/   Windows    (Compose Multiplatform, Java Sound, SQLite over JDBC)
```

Everything that decides *what a song is* lives in `shared/`, so the two platforms
cannot drift apart on it:
- the relevance scorer
- the duplicate heuristic
- the Shazam fingerprinter
- the weather maths
- the search and smart-list grammar ([`SearchQuery`](shared/src/main/kotlin/com/exo/musicplayer/data/library/SearchQuery.kt))

A rule written on one app is read by literally the same code on the other.

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

**Gapless means the same thread keeps going.** The gap between songs used to be
four steps of silence:
1. the decoder ran out
2. the playback thread exited
3. the audio output closed
4. the next song opened everything again

Now the engine is told what comes next, so the same thread carries straight on
through the same open output. Crossfade builds on that: the next song's decoder
opens early and plays underneath the outgoing one, mixed so the loudness doesn't
dip in the middle.

**The volume slider is logarithmic.** Loudness is heard logarithmically, so a
slider that simply scales the samples puts halfway at only 6 dB down, which is
barely audible. Everything useful ends up crammed into the bottom fifth of the
travel. Lucense spreads 40 dB across the slider instead, about 10 dB for each
quarter, and the very bottom is true silence.

**Revert keeps the file's first word, not its last.** The first time anything
overwrites a song's details, the original values are saved, and only that first
time. A second write is the app correcting itself; what you want back is what the
file said before any of it. The snapshot is taken inside the tag writer, so a new
way of writing tags can't bypass it. On Android, which never writes into files at
all, revert simply reads the file again.

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

Both apps take their version from `lucenseVersion` in
[`gradle.properties`](gradle.properties). Bump it for every build you hand out: an
MSI replaces an older install only when its version is higher.

### Bundled tools

The Windows build ships three binaries so downloading works the moment it's
installed, rather than after a first-run setup step. They are fetched, not
committed — ffmpeg alone is 139 MB, past GitHub's file limit, and all three move
faster than this project does.

| tool | licence | used for |
|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense | downloading |
| [ffmpeg](https://github.com/yt-dlp/FFmpeg-Builds) | GPL-3.0 | MP3 conversion, audio out of video, repairing files |
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
  data/library/       duplicate matching, the search and smart-list grammar, genres
app/src/main/java/com/exo/musicplayer/
  share/              the Telegram receive path
  playback/           ExoPlayer service, audio focus, effects, output routing
  data/               Room database, file tags, library repository
desktop/src/main/kotlin/com/exo/musicplayer/desktop/
  audio/              decode, gapless and crossfade, effects, volume, multi-device engine
  data/               SQLite store, cover cache, controller, backups
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
