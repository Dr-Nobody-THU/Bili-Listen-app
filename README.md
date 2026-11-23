# 栖声 · Qi Sheng

栖声 (Qi Sheng) is a personal Android companion app for listening to Bilibili video collections.
It focuses on audio playback, collection queues, progress recovery, favorites, and offline
prefetching instead of trying to replace the full Bilibili client.

## Current Features

- Import BV/AU ids, Bilibili video/audio links (including mobile audio links), and common short links such as `b23.tv`.
  AU tracks use their own audio metadata and stream APIs, and support progress, favorites, special favorites and offline caching.
- Resolve UGC collections and multi-page videos into a continuous audio queue.
- Play in the background through a Media3 foreground service.
- Remember the three most recently played items per collection, including unfavorited ones.
- Keep separate playback order settings per collection:
  `collection order`, `publish time ascending`, and `publish time descending`.
  Queue order choices start collapsed in either orientation; tap the current order to expand them.
  Selecting an order closes the choices, including when entering the special-favorites manual editor.
- Show cover art, author info, queue, title details, playback speed, and play mode controls.
- Favorite multiple collections and restore each collection's saved playback state.
- Heart the currently selected audio/P into the local “特别收藏” playlist from the player. It appears first
  in favorites and the widget picker. Sort by favorite time ascending/descending, or open manual sorting:
  long-press a row's right-hand handle and drag, with edge scrolling for long lists. Done commits the order;
  back/dismiss discards the draft. Manual order is remembered separately from the addition sequence,
  and newly hearted parts append to it. Sorting preserves the currently playing part and its position,
  and applies to next/previous, the widget and cache prefetching. Each BV+CID has its own heart;
  selecting one P never automatically adds other parts. Existing whole-article favorites retain their
  saved parts, which can now be removed independently. Toggle a heart off to remove that part;
  the currently sounding part can finish, then next/previous only visits remaining favorites.
- Removing a collection favorite preserves recent positions and last playback for 30 days. Startup
  cleanup expires that resume state if the collection has not been favorited again, including stale
  widget snapshots. Refavoriting cancels the deadline; removing it again starts a new 30-day window.
  Old unfavorited records with no removal date get 30 days from the first startup after upgrading.
  Listening totals and the shared forest are permanent and independent of this resume-state cleanup.
- Show each collection's cumulative listening time in favorites and the desktop picker.
- Open an author's collection list from the author avatar.
- Automatically cache the current item plus the next five items on Wi-Fi, only for favorited collections.
  Unfavoriting stops prefetching; existing complete files remain usable. Each app opening checks for files
  with no favorite owner for 30 days and removes them. Refavoriting resets the deadline, and a special
  favorite protects its individual file. Legacy files without an ownership date get a full 30-day grace
  period from the first check. Cache cleanup never changes cumulative listening time.
- Show per-item cache status, download progress, sizes and failures in the queue.
- Locate the current episode with a small floating button when it leaves the queue viewport;
  the episode aligns at the top where possible, clamping at the list end so the last row stays at the bottom.
  Bottom sheets stop upward overscroll at the expanded boundary while retaining downward dismissal.
- Fade audio in/out over 400 ms on play/pause, with a persistent playback-settings switch
  (enabled by default). App, system and widget controls share the same transition behavior.
- Optionally mix 13 offline background sounds under the program with independent volume:
  white/pink/brown noise, rain, storm, waves, stream, wind, birds, summer night, fireplace,
  coffee shop and train. The feature defaults to off and remembers the chosen sound/volume.
- Prefer complete local audio files; switch the current stream to its completed cache without
  losing position or pause state. Wi-Fi prefetch requests are bound to that network.
- Disable mobile streaming by default, with an explicit switch in playback settings.
- Keep the just-left item cached for going backwards; prune it on the following transition
  unless it is also in the upcoming six-item window. Apply a 1 GiB budget across collections.
- Expose previous/next commands to Android system media controls and headsets.
- Control playback from 4x2 and 2x2 home-screen widgets, with cover, title, author and progress.
- Switch favorite collections directly from a floating picker on the wide widget.
- Optionally show a dedicated dark music lock screen with clock, vinyl artwork and transport
  controls; swipe right to return to the system keyguard.
- Open background audio and music lock-screen options as sliding pages within playback settings.
  Back/right-swipe returns to settings; swiping the sheet down closes the entire settings flow.
- Use a rotating vinyl cover and an animated tonearm in the dark player screen.
- Optionally enable “长按唱片切换合集” in playback settings (off by default). Long-press the record
  to browse favorite collections in a circular, snapping record carousel. Swipe left/right in portrait
  or up/down in landscape, with neighboring records visible along that axis. Records softly fade into
  the panel at the viewport edges, and the collection name follows the preview. Browsing never changes playback; tapping
  the centered record confirms and resumes that collection's saved position. Outside taps or Back
  cancel without activating the controls underneath. The forest does not enter this mode.
  Collection order is frozen during browsing, and choosing the current collection never reloads it.
- Keep buttons' selected colors without adding a pale rectangular press overlay, across the player,
  settings, music lock screen and widget collection picker.
- Tap the record to show one shared forest grown from the app's total listening time; tap again to return.
  Dimetric island artwork follows the phone's local calendar with spring, summer, autumn and winter palettes.
- Switch between the default mint night and optional black/blue skin in playback settings.
  The selection persists and applies to the player, settings, collection picker and music lock screen.
- Use a thicker lock-screen progress bar and larger white transport icons without a filled play-button circle.
- Distribute the five main playback buttons with even gaps, adapting to narrower phone screens.
- Anchor import/favorites at the top and playback information/controls at the bottom;
  size and center the record in the remaining space, including shorter phone screens.
- Adapt to landscape windows: align the compact import/favorites row with program details/controls
  in the right column, reserving the full-height left column for a larger record/forest.
  The landscape import row keeps the same 48 dp height before and during editing. Reduced top spacing
  leaves an extra line of room for the episode count without changing system safe insets. The queue
  and background-sound picker put options beside their scrollable lists. The music lock screen places its clock/record beside
  playback controls. Side cutouts and system navigation insets are respected in either rotation.
  Layout follows available window dimensions in dp; forest selection survives rotation and state
  restoration without dispatching playback or seek commands. Portrait retains its stacked layout.

Landscape render/interaction tests cover 864x393 dp (Reno14 proportions), a shorter 740x360 dp
window and simulated side/system inset space, in both skins. Physical rotation and ColorOS system
bars still need a check on the actual phone.

## Listening forest

All recorded collections' cumulative listening times grow one shared forest, including unfavorited
collections and single videos. Existing totals are summed without migrating or resetting records;
changing the selected collection or favorites does not change the forest. One small tree is earned
per 24 hours; four small trees merge into a medium tree (96 hours), and four medium trees into a
large tree (384 hours). These are the three tiers, with ground footprints of 1x1, 2x2 and 4x4.
Large trees accumulate without further merging. Existing listening totals are reinterpreted by
this rule without changing stored time. Partial progress grows a seedling and is never discarded
when trees merge. The view shows the totals, tree counts and time remaining to the next small tree.

The same actual elapsed-playback counter used by collection statistics supplies this view: seeking,
playback speed, changing views and season changes do not create listening time. Per-collection totals
remain available in favorites. The whole forest is shown on one continuous island without pagination;
the camera fits its full bounds to the artwork area and automatically zooms out as it expands.
Large layouts use indexed positions, shared tree artwork and screen-sized cached layers. At distant
scales, simplified canopy areas account for all mature trees without allocating a sprite for each one.
Seasons use fixed calendar dates for the four season-opening solar terms: spring starts
February 4, summer May 5, autumn August 7 and winter November 7. These dates repeat every year,
using the phone's local date, without annual astronomical calculations. Winter continues across
New Year until February 4; the landscape refreshes while visible and when the app resumes.

The forest uses illustrated foliage, textured bark, cedar and broadleaf silhouettes, moss, flowers,
stones and a small pond. Tree crowns sway at different rhythms; spring has petals and butterflies,
summer has fireflies, autumn falling leaves, and winter snow. Water ripples and growing seedlings
add gentle motion. Static artwork is cached separately from the moving layers. The animation runs
at about 30 fps only while the forest is visible in a resumed, focused window, pauses in the
background or beneath dialogs, and respects Android's disabled-animation setting.

## Build

This project uses the Gradle wrapper and Android Gradle Plugin.

On Windows, the helper finds Android Studio's JDK in `E:\Program Files\Android\Android Studio`,
uses the project-local Gradle cache, and handles Chinese project paths in JVM argument files.
It builds the APK, runs regression tests and Android lint:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\android.ps1
```

The app compiles with JDK 17; Android 16 regression tests use JDK 21 (bundled with Android Studio).
`local.properties` must point to the installed SDK (on this machine, `E:\Android\Sdk`).
With `JAVA_HOME` and the SDK configured, an APK-only build is also available:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` is intentionally ignored because it contains the local Android SDK path.

For updates to the existing personal installation, keep the original debug signing key at
`.signing/debug.keystore`. The build uses this project-local key when present; otherwise
Android's default debug key is used, which may differ on another computer. The `.signing/`
directory is ignored by Git. Back up the original key separately and keep using it for
future updates so Android can replace the app while preserving favorites and progress.

## Project Layout

- `app/`: Android app implementation.
- `src/`: Java prototype used to validate queue, progress, and audio URL behavior.
- `scripts/`: helper scripts for the Java prototype.
- `fixtures/`: sample local queue data for prototype tests.

## Notes

The app currently targets personal use. Restricted content and login/cookie import are
not implemented yet.

## Name, icon and music lock screen (0.5)

The app is now named **栖声 (Qi Sheng)**. Its application ID, signing key and preference/cache
names are unchanged so existing installations retain their data. The launcher uses a minimal
mint record mark generated with ImageGen, a dark adaptive background, and a matching monochrome
layer for themed icons. The system notification uses a small white version of the same mark.

Open playback settings -> 音乐锁屏 -> 预览与权限. The feature defaults to off. A preview is
available without granting permission or enabling automatic display. Enabling asks for Android's
"Display over other apps" special access because [background activity launches are restricted](https://developer.android.com/guide/components/activities/secure-bal).
Permission is checked again each time; denying/revoking it does not interrupt normal playback.

While the existing playback service is running, a dynamically registered SCREEN_ON receiver
opens the page only if playback is requested, audio focus is not suppressed, the screen is
interactive, the system keyguard is locked, and the user enabled the feature and granted access.
There is no full-screen notification, Accessibility service, wallpaper replacement, always-on
screen, periodic alarm or wake-up request. The non-exported activity uses setShowWhenLocked,
shares playback with the existing service, and does not call requestDismissKeyguard. Pausing on
the page leaves controls available until it is closed. Swiping right/back or tapping the exit
returns to the original keyguard; successful system unlock or screen-off closes the page.

Automatic display on ColorOS 16.0.8 still needs a device check: vendor background-window
policies can add restrictions beyond Android's standard permission. The settings sheet includes
an application-settings shortcut. The regular system media card remains available.

## Playback and offline behavior (0.3)

- Collection order always restores the original collection positions. Publication sorting
  keeps the current audio and position; equal dates use collection position as a tiebreaker
  (reversed for descending order, including multi-part videos). Missing season dates are
  fetched when sorting on Wi-Fi; missing values remain at the end and are labelled in the UI.
- Saved collections can open and advance without fetching queue metadata on mobile/offline.
  A cached file is selected before any audio URL request. Cover images only use local image
  caches outside Wi-Fi. Importing a new link or opening an author's collections still needs
  metadata requests.
- Only finalized, nonempty downloads count as cached. Known response lengths must match;
  partial downloads are never selected for playback. One failed download does not block the
  remainder of the prefetch window. The queue offers a Wi-Fi retry button.
- The player says whether its actual source is local or online; an available cache badge
  alone is not treated as proof that playback is using that file.
- Switching via the app, media controls or widgets resumes the item's recent position.
  Only the three most recently heard items are retained per collection, including the current
  item; revisiting an item makes it recent. Finished items start from zero. Older progress is
  migrated on first use, and positions survive process restarts. Unfavorited collections' resume
  records expire at startup after a 30-day grace period; cumulative listening time is kept.
- The cache retains the item just left, the current item and the next five. On A -> B -> C,
  A is removed at C unless it is already in the upcoming window of a short/circular queue.
  Cache details and retry remain in the queue sheet; the separate home-screen cache card is gone.

## Home-screen widgets

Long-press an empty area of the Android home screen, open its widget picker, then choose
`栖声` and either the 4x2 horizontal player or the 2x2 compact player. The exact grid
dimensions depend on the launcher. Both layouts adapt to smaller heights and long titles.

Play/pause and previous/next send a foreground-service PendingIntent directly to the player,
without opening the activity. Tapping the title opens the app. The last queue and position are
saved so the widget can resume after process death; a fresh installation first needs a program
selected in the app. The progress bar is read-only and updates each second while playing with
the screen on. Pause and errors update immediately; no periodic widget alarm runs when idle.
Widgets keep the existing mobile-data policy, so uncached audio needs Wi-Fi unless mobile
streaming was explicitly enabled. Artwork uses local image caches outside Wi-Fi.

The wide widget has square cover art at the upper left, title/queue position/author on its
right, centered transport controls, and a thicker progress bar between elapsed/total time.
The lower-left collection button opens a dark floating activity over the launcher; it has
its own task and no entry in Recents. Selecting a favorite resumes its latest saved position
through the playback service without opening the main player or refreshing network metadata.
This uses a direct activity PendingIntent, as described in Android's
[activity launch guidance](https://developer.android.com/guide/components/activities/secure-bal).

## Listening time (0.4.1)

Totals measure monotonic elapsed time while Media3 reports actual playback. Pauses,
buffering, focus interruption and time spent resolving a source are excluded. Seeking
adds no time, and playback speed does not multiply the elapsed time. The service checkpoints
every five seconds and at playback transitions, including while the app UI is closed.

Totals start with this version: old resume positions cannot reveal historical listening time.
Collection IDs keep totals separate even when two collections contain the same video.
Totals are independent of the recent-three-position store and survive progress clearing,
ordering changes, removing/re-adding a favorite and normal app updates. Listening is tracked
for collections as they play and displayed when they are in favorites. An abrupt process
kill may lose the few seconds since the last checkpoint; no stopped interval is counted on restart.

## Background audio (0.4)

Open playback settings, then the background-sound picker. Selecting a sound enables it;
the switch turns it off without forgetting the sound or volume. It plays only alongside
the main program, follows pause/focus loss, and shares the existing playback fade.
There is no second media session, network stream or independently running audio player.

Noise is generated continuously. Environmental recordings are packaged as mono 22050 Hz
PCM loops, with a 1.45-6 second equal-power overlap baked into each loop, matched RMS
levels, bounded peaks and continuous interpolated circular reads. A 400 ms ramp also
smooths sound/volume changes. The mixer runs after Media3's Sonic processor so program
speed changes do not accelerate the ambience. Float output, audio offload and hardware
speed changes are disabled to keep this PCM path active. With background audio disabled
and its transition completed, the original PCM is passed through unchanged.

Packaged recordings and their edits are credited in `app/src/main/assets/background/SOURCES.txt`,
also accessible in the picker. Upstream files are pinned to a specific Blanket commit,
and `manifest.json` records original/prepared hashes and loop metrics. Rebuild them using
`scripts/prepare_background_audio.py` with the versions in `scripts/requirements-background-audio.txt`; `--direct` optionally
bypasses a stale system HTTP proxy for that one process. No build-time download is required
for a normal Android build.

### Playback/cache regression fix (0.5.1)

The 0.4 mixer introduced a startup failure when background sound was disabled: Media3 can
feed its shared empty buffer before any PCM arrives, and the dry branch tried to copy that
buffer into itself (`IllegalArgumentException: The source buffer is this buffer`). Empty
input is now ignored without discarding pending output. Tests reproduce the original
failure and compare disabled output with the original Media3 chain on API 28/36, including
warmup, speed changes, sample-rate changes and renderer resets/cache handoffs.

Renderer/decoder/output failures no longer delete complete local audio files or report
them as corrupt. Only missing, malformed or explicitly truncated sources invalidate a cache.
Playback retries preserve the requested pause state. Cancelling a prefetch job is distinct
from losing Wi-Fi, including IO cancellation that clears the thread interrupt flag; actual
timeouts remain retryable failures. Logs now retain nested Media3 error causes and codes.

Design references: Apple's [background sound controls](https://support.apple.com/guide/iphone/play-background-sounds-iphb2cfa052c/ios),
Audacity's [clip crossfades](https://manual.audacityteam.org/man/crossfade_clips.html),
and Blanket's [sound collection and licensing](https://github.com/rafaelmardojai/blanket/blob/bbc88ed3b9dfa8b21805d77286f3183abf88f36f/SOUNDS_LICENSING.md).

Regression tests cover queue order/restoration, cache completeness/eviction, bounded recent
positions, network policy, favorite persistence, system media commands on API 28/33/36,
widget rendering/control/cold restoration on API 28/36, gain ramps and rapid media-controller
toggles, offline ambience loops, mixing/speed isolation on API 28/36, and Compose queue
location/animation/settings interactions.
UI render images are written to `app/build/reports/ui/`; test and lint reports are under
`app/build/reports/`. Device-specific ColorOS media-card layout and physical Wi-Fi/mobile
handover and home-screen widget placement still need confirmation on the OPPO Reno14 / ColorOS 16.0.8.
