# Bili Listen

Bili Listen is a personal Android companion app for listening to Bilibili video collections.
It focuses on audio playback, collection queues, progress recovery, favorites, and offline
prefetching instead of trying to replace the full Bilibili client.

## Current Features

- Import BV ids, regular Bilibili video links, and common short links such as `b23.tv`.
- Resolve UGC collections and multi-page videos into a continuous audio queue.
- Play in the background through a Media3 foreground service.
- Remember playback progress for favorited collections.
- Keep separate playback order settings per collection:
  `collection order`, `publish time ascending`, and `publish time descending`.
- Show cover art, author info, queue, title details, playback speed, and play mode controls.
- Favorite multiple collections and restore each collection's saved playback state.
- Open an author's collection list from the author avatar.
- Automatically cache the current item plus the next five items on Wi-Fi.
- Prefer cached audio files when available, and fall back to live audio URLs otherwise.

## Build

This project uses the Gradle wrapper and Android Gradle Plugin.

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` is intentionally ignored because it contains the local Android SDK path.

## Project Layout

- `app/`: Android app implementation.
- `src/`: Java prototype used to validate queue, progress, and audio URL behavior.
- `scripts/`: helper scripts for the Java prototype.
- `fixtures/`: sample local queue data for prototype tests.

## Notes

The app currently targets personal use. Restricted content, login/cookie import, and a
polished cache-management UI are not implemented yet.
