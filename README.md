# SoundRecordingLib

A **PaperMC plugin library** that intercepts Minecraft sound packets at the Netty layer, records them on a **tick-based timeline**, and replays them to one or more players — immune to lag-spike distortion.

> **⚠ This is a library plugin.** It provides a public API (`SoundRecordingApi`) for other plugins (e.g. [SoundRecord](https://github.com/TWME-TW/SoundRecord)) to start/stop recordings and playback sessions. It has no commands on its own.

---

## Features

- **Packet-level interception** — captures `SOUND_EFFECT` / `ENTITY_SOUND_EFFECT` packets via [packetevents](https://github.com/retrooper/packetevents), not Bukkit sound events.
- **Tick timeline** — all events are stored relative to `Bukkit.getCurrentTick()`, eliminating distortion from server lag spikes.
- **Two recording modes**:
  - `STATIC` — fixed world reference point (ideal for redstone music, scene recordings).
  - `PLAYER_RELATIVE` — records relative to the recorder's instantaneous position (ideal for first-person walkthroughs).
- **Sound category filtering** — whitelist specific `SoundCategory` values per recording session.
- **Tick-based playback** — single periodic task with cursor advancement, supports variable speed, loop, skip threshold, and natural catch-up after lag.
- **Advanced coordinate resolution** — independent flags for `followReplayer`, `applyRecorderOrientation`, `applyReplayerOrientation`, and `speakerMode`.
- **Pause / resume** playback sessions.
- **JSON persistence** — built-in `JsonRecordingStorage` for save/load/list/delete.
- **Thread-safe** — `ReentrantReadWriteLock` guarded draining from Netty I/O threads.
- **Disconnect handling** — auto-stops recordings and removes players from playback targets on `PlayerQuitEvent`.

---

## Dependencies

| Dependency | Version | Scope |
|---|---|---|
| [Paper API](https://papermc.io) | `1.21.11-R0.1-SNAPSHOT` | provided |
| [packetevents](https://github.com/retrooper/packetevents) | `2.12.2-SNAPSHOT` | provided |
| [Gson](https://github.com/google/gson) | `2.10.1` | compile (shaded) |

---

## Installation

1. Place `SoundRecordingLib-1.0.0.jar` and `packetevents` jar in your server's `plugins/` folder.
2. Restart or reload the server.
3. Your plugin that depends on SoundRecordingLib must declare `depend: [SoundRecordingLib]` in its `plugin.yml`.

---

## Quick Start (API)

### Recording

```java
// STATIC mode — record sounds relative to the player's current position
RecordingSession session = SoundRecordingApi.startRecording(player,
    RecordingOptions.builder("my-music")
        .mode(RecordingMode.STATIC)
        .build());

// ... later ...

SoundRecording recording = SoundRecordingApi.stopRecording(player);
```

```java
// PLAYER_RELATIVE mode — record sounds relative to the recorder's moving position
SoundRecordingApi.startRecording(player,
    RecordingOptions.builder("my-walk")
        .mode(RecordingMode.PLAYER_RELATIVE)
        .build());

SoundRecording recording = SoundRecordingApi.stopRecording(player);
```

### Playback

```java
// Default playback (STATIC replay at world coordinates)
PlaybackSession session = SoundRecordingApi.startPlayback(
    recording,
    List.of(player1, player2),
    PlaybackOptions.defaults());

// First-person playback (full orientation reconstruction)
PlaybackSession session = SoundRecordingApi.startPlayback(
    recording,
    List.of(player),
    PlaybackOptions.firstPerson());

session.pause();
session.resume();
SoundRecordingApi.stopPlayback(session);
```

### Persistence

```java
RecordingStorage storage = new JsonRecordingStorage(plugin.getDataFolder().toPath().resolve("recordings"));

storage.save(recording);                                     // save to disk
Optional<SoundRecording> loaded = storage.load("my-music");  // load by name
List<String> names = storage.listNames();                    // list saved recordings
storage.delete("my-music");                                  // delete
```

### RecordingOptions builder

| Method | Description |
|---|---|
| `mode(RecordingMode)` | `STATIC` (default) or `PLAYER_RELATIVE` |
| `reference(Location)` | Fixed reference point (STATIC only; defaults to player position) |
| `filter(SoundCategory...)` | Whitelist of categories to record (null = all) |

### PlaybackOptions builder

| Method | Default | Description |
|---|---|---|
| `anchor(Location)` | `recording.refPos` | Override the playback anchor point |
| `offset(x, y, z)` | `0, 0, 0` | Fine offset added to the final sound position |
| `speed(double)` | `1.0` | Playback speed |
| `loop(boolean)` | `false` | Loop the recording |
| `skip(int)` | `0` | Skip events older than N ticks behind cursor |
| `followReplayer(boolean)` | `false` | Sound position follows the listener |
| `applyRecorderOrientation(boolean)` | `false` | Remove recorder head-rotation offset |
| `applyReplayerOrientation(boolean)` | `false` | Apply listener head-rotation for stereo direction |
| `speakerMode(boolean)` | `false` | Virtual speaker — all sounds directed toward anchor |

---

## Building

Requires **Java 21** and **Maven**.

```bash
mvn clean package
```

The shaded jar will be at `target/SoundRecordingLib-1.0.0.jar`.

---

## Project Structure

```
src/main/java/dev/twme/soundRecordingLib/
├── SoundRecordingLib.java          # Plugin main class
├── api/                            # Public API (SoundRecordingApi, sessions, options)
├── data/                           # Data model (RecordingMode, RecordedSoundEvent, SoundRecording)
├── recording/                      # Recording manager & active recording state
├── playback/                       # Playback manager & active playback state
├── listener/                       # Packet & Bukkit event listeners
└── storage/                        # Persistence (RecordingStorage interface + JSON impl)
```

---

## Design

See [SPEC.md](SPEC.md) (in Chinese) for the full design specification covering:

- Architecture & module structure
- Tick normalization and the lag-spike problem
- Recording & playback flow with thread-safety details
- Coordinate resolution algorithm (all orientation flags and speaker mode)
- Design decisions (D1–D8) and resolved discussion items (E1/E2)

---

## License

This project is open source. See the repository for license details.

---

## Author

**TWME-TW** — [GitHub](https://github.com/TWME-TW)
