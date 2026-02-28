# Minnal - Android Torrent Client

Minnal (Tamil for "lightning") is a fully-featured Android BitTorrent client built entirely in Kotlin with Jetpack Compose. It includes a **pure Kotlin BitTorrent protocol implementation** with no third-party torrent libraries.

## Architecture

The project follows **MVVM Clean Architecture** with a **multi-module** Gradle structure for scalability and separation of concerns.

```
Minnal/
├── app/                    Android application (navigation, DI, service)
├── libtorrent/             Pure Kotlin BitTorrent library (no Android deps)
├── core/
│   ├── common/             Shared utilities, extensions, constants
│   ├── domain/             Domain models, repository interfaces, use cases
│   ├── data/               Repository implementations, local storage, engine wrapper
│   └── ui/                 Shared Compose theme, colors, reusable components
└── feature/
    ├── torrentlist/        Torrent list screen with download management
    ├── addtorrent/         Add torrent from .torrent file or magnet link
    └── settings/           App settings (speed limits, connections, storage)
```

### Module Dependency Graph

```
app
├── feature:torrentlist → core:domain, core:common, core:ui
├── feature:addtorrent  → core:domain, core:common, core:ui, libtorrent
├── feature:settings    → core:domain, core:common, core:ui
├── core:data           → core:domain, core:common, libtorrent
├── core:ui             → core:domain
├── core:domain         → core:common, libtorrent
├── core:common         (no project deps)
└── libtorrent          (no project deps, pure Kotlin)
```

## Features

### Torrent Management
- Add torrents from `.torrent` files or magnet links
- Pause, resume, and remove individual torrents
- Delete downloaded files when removing
- View download progress, speed, ETA, and peer count
- Automatic seeding after download completes

### BitTorrent Protocol (libtorrent module)
- **Bencode** encoder/decoder (BEP 3)
- **Torrent file parser** supporting single-file and multi-file torrents
- **HTTP tracker client** with compact peer list support
- **UDP tracker client** (BEP 15) with exponential backoff retries
- **Peer wire protocol** - full implementation of all standard messages
- **Rarest-first piece selection** for optimal download efficiency
- **SHA-1 piece verification** ensuring data integrity
- **Choking algorithm** - unchoke top 4 uploaders + optimistic unchoke
- **Multi-file support** with correct piece-to-file mapping
- Incoming peer connections via server socket

### Android Integration
- **Foreground service** for background downloads with persistent notification
- **Runtime permission handling** (notifications, storage)
- **Intent filters** for `.torrent` files and `magnet:` URIs
- **Boot receiver** for auto-resuming downloads after reboot
- **Scoped storage** compatible (targets API 36)

### UI
- Material 3 design with dynamic color support (Android 12+)
- Dark/light theme
- Animated progress bars with status-based colors
- Pull-to-refresh, swipe-to-delete, long-press context menus
- Settings with speed limits, connection limits, and behavior toggles

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.2.21 (100%) |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM + Clean Architecture + UseCases |
| DI | Koin |
| Async | Kotlin Coroutines + Flow |
| Navigation | Navigation Compose |
| Persistence | File-based storage + DataStore Preferences |
| Build | Gradle with Version Catalog |
| Min SDK | 24 (Android 7.0) |
| Target SDK | 36 (Android 15) |

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test
```

## Permissions

| Permission | Purpose |
|-----------|---------|
| `INTERNET` | BitTorrent peer/tracker communication |
| `ACCESS_NETWORK_STATE` | Network availability checks |
| `FOREGROUND_SERVICE` | Background download service |
| `FOREGROUND_SERVICE_DATA_SYNC` | Data sync service type (API 34+) |
| `POST_NOTIFICATIONS` | Download progress notifications (API 33+) |
| `WAKE_LOCK` | Prevent CPU sleep during downloads |
| `RECEIVE_BOOT_COMPLETED` | Auto-resume downloads after reboot |
| `WRITE_EXTERNAL_STORAGE` | File storage (API < 29 only) |

## Project Structure Details

### `:libtorrent` - Pure Kotlin BitTorrent Library

A standalone BitTorrent library with zero Android dependencies. Uses only:
- `kotlinx-coroutines-core` for async operations
- `java.net.Socket` / `DatagramSocket` for networking
- `java.security.MessageDigest` for SHA-1 hashing
- `java.io.RandomAccessFile` for file I/O

**Packages:**
- `bencode` - Bencode encoder/decoder with typed element hierarchy
- `model` - Data models (TorrentMetadata, TorrentFile, Peer)
- `parser` - Torrent file parser with info hash computation
- `tracker` - HTTP and UDP tracker clients with announce management
- `peer` - Peer wire protocol messages and connection management
- `piece` - Piece tracking, rarest-first selection, SHA-1 verification
- `file` - File I/O with piece-to-file mapping for multi-file torrents
- `session` - Single torrent download/upload orchestration
- `engine` - Multi-torrent engine with connection management

### `:core:domain` - Domain Layer

Contains business logic independent of any framework:
- **Models**: `Torrent`, `TorrentStatus`, `TorrentSettings`, `TorrentFileInfo`
- **Repository interfaces**: `TorrentRepository`, `SettingsRepository`
- **Use cases**: `AddTorrentUseCase`, `GetTorrentsUseCase`, `PauseTorrentUseCase`, `ResumeTorrentUseCase`, `RemoveTorrentUseCase`, `GetTorrentDetailUseCase`, `GetSettingsUseCase`, `UpdateSettingsUseCase`, `AddMagnetUseCase`

### `:core:data` - Data Layer

Implements repository interfaces with concrete storage and engine integration:
- **TorrentStorage** - File-based persistence for torrent entities
- **TorrentEngineWrapper** - Bridges the libtorrent engine to Android
- **SettingsRepositoryImpl** - DataStore Preferences for app settings
- **TorrentMapper** - Maps between engine, entity, and domain models

### Feature Modules

Each feature module contains a ViewModel and Compose screens:
- **torrentlist** - Main screen with torrent list, progress, and management actions
- **addtorrent** - Add new torrents from files or magnet links
- **settings** - Configure speed limits, connections, and behavior

## License

See [LICENSE](LICENSE) file.
