# Miniwechat Mesh Chat Architecture

## 1. Product Overview
- **Goal**: Android 14–16 mini app that lets two or more nearby devices chat only when both are online. Messaging flows through Google Nearby Connections using a simple flood-based mesh, with iMessage-inspired UI and WhatsApp-style delivery states.
- **Key Capabilities**:
  - Dual-pane chat UI with member list, conversation pane, message composer, custom nicknames, and delivery indicators.
  - Offline queue backed by Jetpack DataStore; messages retry every 5 seconds until peers return online or the user cancels.
  - Diagnostics overlay toggle plus persistent log files (auto-rotate at 1 MB, manual clearing, on-device viewer).

## 2. Architecture Layers
```
┌────────────┐    ┌────────────────────────────────┐
│ UI Layer   │ -> │ ChatViewModel / SettingsVM     │ -> exposes StateFlow for chat, members, logs
└────────────┘    ├────────────────────────────────┤
                   │ ChatRepository                │ -> mesh events, cache, offline queue
                   │ SettingsRepository            │ -> diagnostics toggle storage
                   │ LogManager                    │ -> rolling log files
                   └────────────────────────────────┘
                              │
                     ┌─────────────────────┐
                     │ NearbyChatService   │ -> Google Nearby P2P_CLUSTER, flood routing
                     │ ChatCacheDataSource │ -> Proto-style DataStore caches
                     └─────────────────────┘
```
- **UI Layer** (Compose): `ChatScreen`, `SettingsScreen`, `LogsScreen` provide the chat surface, toggles, and log viewer. Diagnostics bubble floats above content for 3 seconds or until dismissed.
- **State Layer**: `ChatViewModel` owns the mesh session, exposing members, messages, and diagnostics state. `SettingsViewModel` controls the diagnostics switch and log utilities.
- **Data Layer**: `ChatRepository` synchronizes DataStore cache, offline queue, and Nearby events while writing diagnostics to `LogManager`. `ChatCacheDataSource` stores conversations/members, keyed by conversation/member IDs. Settings live in Preferences DataStore.
- **Platform Layer**: `NearbyChatService` wraps Google Nearby Connections (P2P_CLUSTER) with simple flood routing, endpoint tracking, and connection diagnostics.

## 3. Key Flows
1. **Session Bring-up**: ViewModel requests `ChatRepository` to start mesh; `NearbyChatService` advertises+discovers, accepts connections, emits `MemberOnline` events which update the cache and UI.
2. **Message Send**: Composer enqueues a `ChatMessage (QUEUED)` → DataStore → repository tries immediate send → marks `SENT` or `FAILED`; flood job retries every 5 seconds until success or user cancels via long-press.
3. **Message Receive**: `NearbyChatService` decodes `MeshEnvelope`, deduplicates `packetId`, emits to repository, which stores it as `DELIVERED` and optionally re-broadcasts if hop budget remains.
4. **Diagnostics**: Any Nearby/connectivity failure creates a `DiagnosticsEvent`; repository logs it through `LogManager`, emits to viewmodel, and—if enabled—shows the floating bubble for 3 seconds. Logs persist under `files/logs/` with auto-rotation and manual clear.
5. **Settings & Logs**: Diagnostics switch persists to Preferences DataStore. Logs screen calls `LogManager` to read/clear files, providing an in-app audit trail for demos.

## 4. Offline Strategy
- **Queue Storage**: Unlimited queue stored via `ChatCacheDataSource`; entries retain `MessageStatus` and `shouldRelay` metadata.
- **Retry Policy**: Background coroutine checks the cache every 5 seconds, resending `QUEUED/FAILED` messages when at least one peer is online.
- **User Control**: Long-press on a bubble opens quick actions (cancel/dismiss). Cancel removes the message from queue and broadcasts state change.

## 5. Diagnostics & Logging
- **Bubble Toggle**: Settings screen controls whether on-screen diagnostics appear; even when disabled, events still write to disk.
- **Log Files**: `diagnostics.log` auto-rotates at 1 MB (archived as `diagnostics-<timestamp>.log`). Logs screen allows manual refresh and clearing to satisfy the "hand-clean" requirement.

## 6. Testing Plan
- **Unit Tests**:
  - `ChatRepository` queue flushing, status transitions, nickname updates, diagnostics emission.
  - `LogManager` rotation + clear behavior via temporary file system rule.
  - `SettingsRepository` diagnostics toggle persistence.
- **Instrumentation Tests**:
  - Compose UI test verifying diagnostics bubble visibility toggled by settings.
  - Chat screen long-press cancel flow updates status label and disables spinner.
  - Logs screen refresh/clear buttons update the list.

## 7. Future Multi-Party Support
- Mesh already uses P2P_CLUSTER with flood routing; to truly scale beyond two peers, extend `ChatRepository` to maintain per-member hop TTL and implement smarter routing (e.g., tracked neighbor graph or leader-based broadcast). Datastore schema already stores member lists per conversation, so additional peers drop in without schema changes.
