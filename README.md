# Lucense

A music player for **Windows**, **Linux** and **Android** that works on files you
actually own, identifies songs it doesn't recognise, and asks no service for an account.

It started as one workflow — *share a song from Telegram and it stays in your
library* — and grew from there. Nothing in it requires a login, an API key, or a
subscription; every service it talks to is one that answers without them.

**[Download the latest release →](https://github.com/MythicalFentWizard/Lucense/releases/latest)**

---

## Install

| | file | needs |
|---|---|---|
| **Windows** | `Lucense-<version>-Setup.exe` | Windows 10 or 11, 64-bit. Java, yt-dlp, ffmpeg and spotdl come inside it — nothing else to install. |
| **Linux** | `lucense_<version>_amd64.deb`, `lucense-<version>-1.x86_64.rpm`, or `Lucense-<version>-linux-x64.tar.gz` | A 64-bit desktop Linux: Ubuntu 22.04, Debian 12, Fedora or newer. Everything comes inside it here too. |
| **Android** | `Lucense-<version>.apk` | Android 7.0 or newer, on any phone or tablet. |

- **Windows:** `-Setup.exe` is Lucense's own installer: install or update in one
  press, choose where it goes, and open Lucense at the end. It isn't code-signed,
  so SmartScreen may say *Windows protected your PC*. Press **More info → Run
  anyway**. The plain `.msi` is there too, for anyone who'd rather have one.
- **Windows upgrades:** a newer installer updates the Lucense you have, and your
  library, playlists, ratings and play counts stay.
- **Linux:** the `.deb` (Ubuntu, Debian, Mint, Pop!_OS) or `.rpm` (Fedora,
  openSUSE) installs Lucense in the menu and in *Open with* for music files. The
  `.tar.gz` runs from wherever it's unpacked: `Lucense/bin/Lucense`. The media
  keys and the desktop's media controls work through MPRIS, as for any Linux player.
- **Android:** `Lucense-<version>.apk` carries every kind of processor and installs
  anywhere. The smaller ones carry one each: `-arm64` for most phones of the last
  several years, `-armv7` for phones running 32-bit Android (many budget ones, even
  with a 64-bit chip), `-x86_64` and `-x86` for Chromebooks and Intel tablets. If
  one won't install, the plain one will. The APK is debug-signed, so Android asks
  you to allow installs from whichever app you opened it with.

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

Each of Identify's tabs keeps its own box and results while the app is open,
so switching between them never mixes one search's answers into another.

**Discover** is for finding music you don't have yet: a map of genres to
wander, in the manner of Every Noise at Once. It's a tab on Android and a page
on Windows.

| | what you can do |
|---|---|
| **Browse genres** | about 2,200 of them, down to digicore and pirate metal. Filter by a word, or by family: *core* finds hardcore, metalcore and digicore alike. |
| **Hear a genre** | press play beside any genre for half a minute of one of its artists. Press again for a different one. |
| **Open a genre** | see who plays it, the artists it's most of first, the genres next to it, and what those artists released this year. |
| **Type an artist** | see which genres they are, their most played songs, their releases with dates, and who is similar. |
| **New from your artists** | what the artists already in your library put out in the last four months. |
| **You might like** | artists similar to yours that you don't have. |
| **Your YouTube** (Windows) | the songs among what YouTube suggests to the account you signed in with: its home page read, the songs kept and the rest left out. They preview, stream and download as Identify's YouTube results do, and YouTube's Mixes open to their songs. The first songs are looked up ahead while you read the list, so most start as soon as you press them. |
| **Charts** | what is played most right now, overall or by broad genre. |
| **Surprise me** | a random genre with someone in it. |

Any song can be heard for half a minute, and downloaded with one press:
Lucense finds the real upload on YouTube by artist, title and length, as it
does everywhere else. Downloads queue, so you can keep browsing. *Search on
YouTube* is there for picking an upload by hand.

**One small preview player.** Whatever is being previewed, a YouTube result in
Identify or a clip in Discover, shows in a small player above the tabs on a
phone and in the corner of the page on Windows. It says what is playing, pauses
it, and the X closes it, from whichever page you have moved on to.

The genres and who is tagged with them come from MusicBrainz; the songs,
releases, pictures and previews from Deezer. Neither needs an account or a key.
Deezer is blocked in some countries: without it genres still work, and the
page says what it couldn't reach. See [why not Every Noise itself](#why-discover-doesnt-use-every-noise-at-once).

**Lyrics** come from four services in turn (LRCLIB, NetEase, lyrics.ovh, Genius),
keeping timed LRC where it exists so they scroll in sync. Settings lists them
with a tick each, and one unticked is never asked; the cover services have the
same list (YouTube, iTunes, Deezer, Audius, Genius, MusicBrainz), so if you'd
rather no song took a music video's still as its cover, untick YouTube. If all
four lyrics services come up empty, you can paste them in yourself. Timed lyrics that run early or late can
be moved a quarter-second at a time, and each song keeps its own setting.
*Translate* puts the lyrics in your language under each line, the text can be
selected and copied, and *Fullscreen* shows just the lyrics, big, over the
song's blurred cover or over your own background (your wallpaper and moving
background). You choose what gets searched for: the tags
as they are, a tidied title with `(Official Video)`, `[HD]` and `feat. …`
stripped, the title alone, the file name, or your own pattern.

**Moods** matches songs to the weather — not "what you played while it rained",
which just returns your most-played songs, but what you play *disproportionately*
when it rains. See [`WeatherAffinity`](shared/src/main/kotlin/com/exo/musicplayer/data/weather/WeatherAffinity.kt).

**Downloads** from YouTube, SoundCloud, Bandcamp, Spotify and anywhere else
yt-dlp reaches, at your choice of quality, as MP3, M4A, Opus, OGG, FLAC or WAV.
Covers come out square, an uploader's "Artist - Title" is split into proper
tags, and the spoken intros and skits SponsorBlock's listeners have marked in
music videos can be cut. The choices are remembered on both apps.

**Playlists from other services.** Paste a Spotify, Apple Music, YouTube or
SoundCloud playlist (or album) link on the Playlists page. Only the list is
read, from what the service shows anyone with the link, so nothing signs in.
Songs you already have are linked up into a new playlist, and the rest can be
downloaded, each joining the playlist as it arrives.

**Streaming.** *Stream all* on YouTube results, or *Stream it* on a playlist
read from a link, plays the songs online one after another in the small player,
which gains previous, next and a download button. Nothing joins the library or
the queue. The next song is looked up while one plays, so there's no wait
between them.

**Playlists download song by song.** Paste a YouTube playlist link (on Windows,
tick *Download the whole playlist* for a link to one video inside a list) and
each song is its own download: several at a time on Windows, one after another
on a phone, each added to the library as it lands. On Windows the songs already
saved are remembered, so asking for the same playlist again fetches only what
is missing.

**When YouTube says no.** YouTube refuses connections for three reasons: a lot
of downloads in a row, a VPN address it has flagged, or a yt-dlp that has fallen
behind. A playlist paces itself, backs off when a song is refused, and stops
asking after several refusals running rather than making it worse. The message
says so, and what helps: wait an hour, update yt-dlp, or try
another server. Nothing is fetched from anywhere else instead.

**Or sign in to YouTube.** When YouTube refuses your VPN's address with
"confirm you're not a bot", downloads can go as your YouTube account instead.
On Windows, *Sign in here* opens your default browser in a window of its own,
apart from your usual one: sign in to YouTube there, close the window (or press
*Done*), and Lucense keeps that sign-in. The window is the browser as you'd start
it yourself, so Google's sign-in doesn't turn it away. Nothing you browse with
touches the sign-in, so YouTube doesn't cycle it out the way it does a browser's
in daily use, and Lucense keeps up with the cookies YouTube renews as it goes. It can also copy
the sign-in of a browser you're signed in with, and renew the copy every few
days and whenever YouTube stops taking it, or use a cookies.txt file. On
Android it's YouTube's own sign-in page inside the app, or a cookies.txt file.
It's optional and stays on your device. YouTube can flag accounts that download
a lot, so a spare account is safer.

**A failed download can be retried** (Windows): each item in the queue has its
own *Retry*, and a playlist that missed some songs has *Retry the missed*, which
fetches only those.

**Downloads can use their own proxy.** It's in Settings → Downloads on both
apps. If your VPN app also runs a local proxy (v2rayNG and Hiddify listen on
`127.0.0.1:10808`), point that at a cleaner server. On Android only downloads
go through it; on Windows the setting covers everything the app does online.

### Playing

- **Gapless, with optional crossfade.** One song runs straight into the next with
  no silence. Settings → Playback → *Crossfade* overlaps them by 2 to 12 seconds.
- **Volume levelling.** *Level volumes* measures each song once, and after that
  they all play at the same loudness. Nothing is written to the files.
- **A volume slider in decibels** (Windows; a phone has its own volume keys).
  Every part of the slider does something. It takes the mouse wheel, and
  clicking the speaker (or pressing **M**) mutes and unmutes.
- **A seek bar you can grab and drag.** On Windows the drag keeps following the
  mouse even after it leaves the bar.
- **Shuffle and repeat.** Shuffle doesn't interrupt the song that's playing.
  **Previous** restarts the song if you're more than four seconds in.
- **A queue you can edit.**
  - *Play next* and *Add to queue* are on every song.
  - The **Up next** panel shows the real order, shuffle included. From there you
    can move songs up and down, take them out, or clear the queue.
- **Resume where you left off**, even after closing the app. On Android the
  song, the place in it and the whole queue come back after the phone has
  killed the app, and headphone or lock-screen *play* picks up from there.
- **Sleep timer:** 15 to 90 minutes, or *when this song ends*. It fades out over
  the last half minute instead of cutting off mid-bar.
- **Effects:** speed, pitch, distortion, lo-fi, tremolo, 8D, echo, reverb and EQ,
  all combinable, with a spectrum meter. Distortion adds grit without getting
  louder: the level is evened out to what went in.
  *Remember for this song* brings a setting back whenever that song plays.
- **More than one output at once:** several devices play the same audio, exactly
  in step on Windows. Android has no real way to do this, so there it's marked
  experimental.
- **Game ducking** (Windows): a global hotkey turns the music down while you're
  gaming.
- **Trim** any song from its menu: drag the two handles over its waveform, hear
  the cut, and save it as a new song. The cut is lossless, nothing is encoded
  again, and the cover and tags come with it.
- **Mini player** (Windows): a small always-on-top window. There's also a tray
  icon with play/pause, next, previous and quit.

### Finding things

Plain words in the search box work as you'd expect, and accents are ignored, so
`proz` finds *Pröz*. The same box also takes filters:

```
artist:proz   album:wave   title:star   genre:rock   artist:"tyler, the creator"
fav:   unplayed:   rating:4+   stars:<3   year:2015-2020   plays:0   added:30d
```

Combine as many filters as you like. The last eight searches appear as chips
when the box is empty.

**Albums and Artists have a search of their own.** Type any part of a name, or of
who it's by, in any order and with or without accents: `beyonce lemonade` finds
*Lemonade*. On a phone, the search button searches whichever tab is open.

You can sort by artist, title, album, length, times played, time listened, date
added, rating or year. **J** (or *Playing* on Android) scrolls the list to
whatever is playing.

**It remembers how you left it.** The sort order of songs, albums and artists,
the list or grid view, the page you were on and your recent searches all come
back the next time you open the app, on both platforms.

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

### Fixing a messy library

**Bulk tools** (*Library tools* on Android) works through the whole library. Songs a tool has already handled
are skipped unless you tick *Redo*.

| tool | what it does |
|---|---|
| **Names & tags** | looks songs up by name and writes the title and artist, or the album, year and genre, or both — it asks which |
| **Identify by sound** | fingerprints the audio, for files with no usable name at all |
| **Names from folders** | reads `Artist/Album/01 Title` out of the folder tree; fills blanks only (Windows — a phone's library has no folder tree of yours) |
| **Covers** | missing cover art from six sources, trying your preferred one first |
| **Lyrics** | fetches lyrics for everything, keeping timed ones where they exist |
| **Level volumes** | measures loudness so everything plays at the same level |
| **Mass fix Telegram songs** | rebuilds files that came without a proper header, so they show their real length and seek properly — the audio itself is untouched |

**Fix** does the same for just the songs you've selected. It asks which of
tags, genres and lyrics to fetch. Each song is looked up by its name, and one
whose name is only an id, like `9239d7ef-aeb9-…`, is identified by listening
to it instead.

You can also select a batch of songs and set their artist, album, year or genre
all at once.

**Duplicates** finds the same song stored twice by *listening* to it, not by
its name. Each song gets an acoustic fingerprint taken from the middle of the
track, so a 96 kbps copy, a file with no tags at all, or one saved under a
different name all turn up as the same song, while two different songs that
happen to share a title don't. The fingerprints are kept, so only new songs are
listened to the next time. Nothing is removed until you've seen the list.

Files named for the wrong format, such as M4A audio saved as `.mp3`, are read
by what's inside them, so their tags, length, edits and repairs all work.

When a tool guesses wrong, *Revert to the file's own details* puts back what the
file said before anything touched it.

### Around the app

- **Keyboard shortcuts** (Windows). They work whenever you're not typing in a
  box, and **/** lists them all:

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
- **Discord Rich Presence** (Windows). Your profile shows the song, the artist, the song's
  own cover with the Lucense badge on its corner, and a running timer. It clears
  when you pause. It talks to the Discord app on your PC, and there's nothing to
  sign in to.
- **Open with Lucense** (Windows). Settings → Playback can offer Lucense in Explorer's
  *Open with* menu for music files. Windows doesn't let an app make itself the
  default player, so that last step is yours.
- **Lyrics in their own window** (Windows), which can sit anywhere on screen.
- **Themes:**
  - Thirteen published colour schemes: Catppuccin Mocha and Latte, Dracula,
    Tokyo Night, Rosé Pine, Nord, Gruvbox, Everforest, Solarized dark and light,
    One Dark, Ayu Mirage and Daylight.
  - Every scheme is checked for readable contrast.
  - Android has the same twelve schemes beside its own palettes and Material
    You. Building your own theme is Windows-only for now.
- **Colours from the cover:** the theme stays and its accent follows the
  artwork of the song playing, on both apps.
- **Liquid glass** (Windows, in Appearance): the window as panes of frosted
  glass over a slowly drifting glow of the theme's colours, or your wallpaper,
  with rounder controls and lit edges.
- **Its own window frame** (Windows): no Windows title bar or border, but
  Windows itself still moves, sizes and snaps it, so it behaves like any other
  window. Drag the strip along the top to move it, double-click to maximise,
  drag it to an edge or a corner for half or a quarter of the screen, use
  Win+arrows, or hover the maximise button for Windows 11's snap layouts.
  Windows 11 rounds the corners and gives it its shadow.
- **Any window size** (Windows): on a smaller window the sidebar becomes icons,
  the side panel narrows and the buttons fold down, rather than everything
  shrinking.
- **Long lists scroll like Windows ones** (Windows): the song list has a
  scrollbar, and middle-click scrolling as in a browser.
- **Backgrounds and wallpaper:** stars, aurora, fireflies, snow, a scrolling graph
  of the song, or visuals that move with the music, on both apps. You can set
  your own picture behind them. They draw only while there's something to
  show: the visuals that follow the music come to rest when it stops, and a
  still window draws nothing.
- **Backups.** Favourites, ratings, playlists, play counts and listening time go
  to a plain text file. Restoring adds them back and never deletes anything.
- **Stats** on what you listen to, and when.
- **Update check:** Lucense tells you in Settings when a newer build is out.

### On Android

The Android app now does what the Windows one does, wherever a phone allows it:
crossfade, volume levelling, EQ, the sleep timer, ratings, smart lists, *Made
for you*, the library tools, Fix, duplicates by sound, edit many, backups, the
published colour schemes and the moving backgrounds. On top of that it has the
Telegram share that started all this.

Left out, because a phone either can't do them or has its own way:

| Windows feature | on a phone |
|---|---|
| Keyboard shortcuts, tray icon, mini player window | no keyboard or desktop; the notification and lock screen have the controls |
| Discord Rich Presence | Discord's phone app has no way for another app to set it |
| Lyrics in their own window | Android apps don't float windows over each other |
| Game-ducking hotkey | no global hotkeys; Android lowers music for calls and alerts itself |
| Volume slider | the phone's volume keys |
| Watching a folder, Names from folders | shared songs are copied into the app, so there's no folder of yours to watch |
| Open with Lucense | sharing a file to Lucense does this |
| Liquid glass, the window frame | a phone's app fills the screen; there's no window to frame |

There's no splash screen on Android any more: the app opens straight to where
you left it.

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
- the duplicate finder's audio fingerprints ([`AudioPrint`](shared/src/main/kotlin/com/exo/musicplayer/data/library/AudioPrint.kt))
- the Shazam fingerprinter
- what a lyrics search asks for, and the Telegram file-name reader
- the *Made for you* lists and the update check
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

<a id="why-discover-doesnt-use-every-noise-at-once"></a>
**Why Discover doesn't use Every Noise at Once.** It was the obvious source,
and it can't be used. The site has no API, and its `robots.txt` forbids
automated access outright, naming AI agents in particular. Its data also
stopped being updated at the end of 2023, when its author lost access to
Spotify's internals. So
[`Discovery`](shared/src/main/kotlin/com/exo/musicplayer/data/discover/Discovery.kt)
builds the same kind of map from two services that are open and still live.
MusicBrainz says which artists carry a genre tag, but returns the best known
first, so "shoegaze" opened with bands that are mostly something else; the
artists are re-ordered by how much of each one the genre is, and the genres
"next to" one are those the same artists are also filed under. Both services
ask clients to keep their pace down (MusicBrainz one request a second, Deezer
fifty in five), so requests queue behind a small delay and answers are kept on
disk for as long as each kind stays true.

**A YouTube search that finds nothing is asked again in other words.** YouTube
sometimes answers an ordinary query with an empty page: "Bonga Kambua" found
nothing, time after time, while "Kambua Bonga" found the song at once. Before
giving up,
[`YouTubeLinkFinder`](shared/src/main/kotlin/com/exo/musicplayer/data/youtube/YouTubeLinkFinder.kt)
tries the title before the artist, then with "audio" added.

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
# Once, to fetch the tools the desktop build bundles (~200 MB).
bash tools/fetch-tools.sh          # Windows
bash tools/fetch-tools.sh linux    # Linux

# Windows app — runnable image, the MSI, then Lucense-<version>-Setup.exe
# (installer/, which needs the .NET SDK) with the MSI inside it
bash tools/build.sh :desktop:createDistributable
bash tools/build.sh :desktop:packageMsi
bash tools/build.sh :desktop:packageSetup

# Linux app, on Linux: .deb, .rpm and the runnable image
gradle :desktop:packageDeb :desktop:packageRpm :desktop:createDistributable

# Desktop tests; the Linux ones (MPRIS, Discord, the sign-in window) run on Linux only
bash tools/build.sh :desktop:test

# Android — the APKs that ship: app/build/outputs/apk/release/app-universal-release.apk,
# and one per processor type (app-arm64-v8a-, app-armeabi-v7a-, app-x86_64-, app-x86-release.apk)
bash tools/build.sh :app:assembleRelease

# Android tests, on a connected phone or emulator
bash tools/build.sh :app:connectedDebugAndroidTest
```

`tools/build.sh` wraps Gradle with per-host proxy routing, which matters on
connections where some Maven mirrors are reachable and others aren't.

jpackage only packages for the system it runs on, so the Linux packages are made
by [`.github/workflows/linux.yml`](.github/workflows/linux.yml) on GitHub's Ubuntu
22.04. It runs the tests there under a virtual display, a window manager and a
session bus, then installs the `.deb` on clean Ubuntu 22.04 and 24.04 machines,
runs the bundled tools, starts Lucense and takes a screenshot.

The Android release build is shrunk by R8 and signed with the building
machine's debug key, the key every published APK has carried, so each one
installs over the last. It is not debuggable and carries the AndroidX
baseline profiles. On an emulator with a 1,075-song library, it opened in
0.79 s against the debug build's 1.49 s. Scrolling the song list dropped
from 10–16% janky frames to under 0.3%.

Both apps take their version from `lucenseVersion` in
[`gradle.properties`](gradle.properties). Bump it for every build you hand out: an
MSI replaces an older install only when its version is higher.

### Bundled tools

The Windows build ships four binaries so downloading works the moment it's
installed, rather than after a first-run setup step. They are fetched, not
committed — ffmpeg alone is 139 MB, past GitHub's file limit, and all of them
move faster than this project does.

| tool | licence | used for |
|---|---|---|
| [yt-dlp](https://github.com/yt-dlp/yt-dlp) | Unlicense | downloading |
| [ffmpeg](https://github.com/yt-dlp/FFmpeg-Builds) | GPL-3.0 | MP3 conversion, audio out of video, repairing files |
| [spotdl](https://github.com/spotDL/spotify-downloader) | MIT | Spotify links |
| [QuickJS-NG](https://github.com/quickjs-ng/quickjs) | MIT | the JavaScript engine yt-dlp asks for with YouTube |

yt-dlp also gets a small plugin of Lucense's own,
[`lucense_solver.py`](desktop/src/main/resources/yt-dlp/lucense_solver.py): yt-dlp's
QuickJS solver with its cache of prepared YouTube players switched on, which
yt-dlp ships switched off. Preparing the player took QuickJS about five seconds
of every preview and download; kept, it takes a third of a second. If a yt-dlp
update ever breaks the plugin, yt-dlp's own solver takes over.

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

## Artwork

The Lucense cat — the logo and the app icon on both platforms — was drawn by
**Stillinthedesert**: [@Stillinthedesert](https://t.me/Stillinthedesert) on
Telegram, [@zeto0_2](https://www.tiktok.com/@zeto0_2) on TikTok.

---

## Credits

**[Riff](https://github.com/rootscripts/riff)** by **rootscripts**, a free
desktop music player, is where several of Lucense's features come from:
distortion, the download formats, importing playlists from Spotify, Apple
Music, YouTube Music and SoundCloud, fullscreen lyrics, lyrics timing and
translation, colours from the cover, the trimmer and streaming. Lucense is
written in Kotlin and Riff in JavaScript, so these are Lucense's own versions
of Riff's ideas, with Riff's approach followed where it was the right one (how
it reads a Spotify playlist without an account, for example). Riff is MIT
licensed, copyright (c) 2026 rootscripts.

The frosted-glass blur on Windows is drawn with
[Haze](https://github.com/chrisbanes/haze) by Chris Banes, Apache 2.0 licensed.

---

## Contact

Telegram: [@Eth4wn](https://t.me/Eth4wn) — bugs, requests, or just to say it
broke on your machine.

---

## Licence

MIT, see [LICENSE](LICENSE). The bundled tools keep their own licences, listed
above.

Made by lucent.
