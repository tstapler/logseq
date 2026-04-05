# TODO.md - Logseq Kotlin Compose Desktop Re-implementation

## Current Status
- **Migration State**: Feature Implementation Phase - Core features complete!
- **Technology Stack**: Kotlin 2.0.21, Compose Multiplatform 1.7.1, **SQLDelight 2.1.0 (Persistent)**
- **Recent Activity**: Migrated to SQLDelight, fixed hierarchy data integrity bugs, extracted MarkdownEngine.
- **Last Updated**: April 4, 2026
- **Current Focus**: Stability and Multi-platform Support

## Build Status

| Target | Status | Notes |
|--------|--------|-------|
| JVM/Desktop | ✅ PASSING | Stable, SQLDelight persistent, Skiko resolved transitively (0.8.18) |
| Android | ✅ PASSING | Stable, SQLDelight persistent |
| JS | ⏸️ Disabled | BUG-003: OutOfMemoryError |
| iOS | ⏸️ Disabled | Ivy repository issues |

## Project Structure (Multiplatform)

```
kmp/
├── build.gradle.kts           # Kotlin Multiplatform configuration
└── src/
    ├── commonMain/
    │   └── kotlin/com/logseq/kmp/
    │       ├── platform/          # expect classes
    │       ├── model/             # Page, Block, Property
    │       ├── repository/        # SQLDelight implementations
    │       ├── db/                # GraphLoader, DriverFactory
    │       ├── editor/            # Editor core logic
    │       └── ui/                # Compose UI components
    ├── jvmMain/                   # JVM/Desktop implementations
    └── androidMain/               # Android implementations
```

---

## Active Remediation (Post-Review March 2026)

### P0: STABILITY & COMPATIBILITY
- [x] **[JVM-001] Fix Skiko Runtime Crash** - Resolved transitively via Compose 1.7.1 on Kotlin 2.0.21.

### P0: ARCHITECTURE & MAINTAINABILITY
- [x] **[UI-001] Decompose BlockRenderer** - Split 600+ line God Object into BlockGutter, BlockEditor, BlockViewer, BlockItem, BlockList components.
- [x] **[ED-001] Implement Undo/Redo Command Pattern** - Undo/redo stack in JournalsViewModel: lightweight content edits + full page snapshots for structural ops. Ctrl+Z/Ctrl+Shift+Z/Ctrl+Y wired in App.kt.
- [x] **[ED-002] Decouple UI from Editor Core** - Moved `AutocompleteMenu` from `editor.components` to `ui.components`; removed cross-package Compose dependency.
- [x] **[TEST-001] Fix Flaky Test Sync** - Replace `delay(50)` with `UnconfinedTestDispatcher(testScheduler)` + `backgroundScope` for deterministic, non-hanging tests.

### P0: DATA ARCHITECTURE (Replication & Merge Readiness)
- [x] **[DB-001] UUID-Native Block Storage** - Moved from numeric IDs to UUID-native storage across the entire application (Schema, Models, Repositories, GraphLoader, ViewModels). Enables cross-device merge, content deduplication, and replication support.
  - [x] Phase 1: Schema migration (UUID PKs, FTS5 compat, query rewrites)
  - [x] Phase 2: Model & repository layer (remove `id: Long`, UUID-only identity)
  - [x] Phase 3: GraphLoader UUID-native loading (populate `left_uuid`, content hashing)
  - [x] Phase 4: Test refactor (all 130+ tests updated to UUIDs)
  - [x] Phase 5: Extra: Removed Encryption logic (EncryptedRepositories.kt) as per user request
  - [x] Phase 6: Extra: Implemented file system watcher in GraphLoader for auto-reload from disk
  - [x] Extra: Added `generateTodayJournal()` in JournalsViewModel
  - [x] Extra: Implemented large-scale deletion safety check in GraphWriter

### P1: FEATURE COMPLETION
- [x] **[OPS-001] Implement Subtree Operations** - `promoteSubtree`, `demoteSubtree`, and `duplicateSubtree` are implemented in `BlockTreeOperations.kt` and delegated to from `BlockOperations.kt`. `moveBlockEnhanced` is also implemented with positioning support and sibling shifting.
- [x] **[FTS-001] Native Search Optimization** - `searchWithFilters` and `searchBlocksByContent` now use FTS5 instead of loading all blocks into memory; FTS query sanitized to prevent syntax errors.
- [x] **[MG-001] Multi-Graph Support** - Allow users to manage multiple knowledge graphs with per-graph SQLite databases.
  - [x] Phase 1: Foundation (hashing, GraphInfo model, canonicalizePath, databaseUrlForGraph) - COMPLETED
  - [x] Phase 2: Repository lifecycle (GraphManager, driver close/open, StateFlow<RepositorySet>) - COMPLETED
  - [x] Phase 3: ViewModel + UI integration (graph switcher, key-scoped ViewModels) - COMPLETED
  - [x] Phase 4: Migration + polish (single-DB migration, remove graph, status bar) - COMPLETED

---

## Completed Work

### ✅ Build & Persistence (March 2026)
- [x] **SQLDelight Migration**: Replaced In-Memory/DataScript with persistent SQLite backend.
- [x] **Atomic Hierarchy Sync**: `left_id` sibling chain correctly maintained in DB.
- [x] **Reactive DB Flows**: UI updates automatically via SQLDelight observers.
- [x] **Markdown Engine**: Extracted regex parsing into standalone `MarkdownEngine`.

### ✅ Core Infrastructure
- [x] PlatformFileSystem with all file operations
- [x] GraphLoader with progressive loading
- [x] Block and Page repositories (SQLDelight)
- [x] Performance monitoring infrastructure

### ✅ UI Framework
- [x] Main application window with menu bar
- [x] Theme toggle (Light/Dark/System)
- [x] Left sidebar with navigation
- [x] Right sidebar (expandable)
- [x] **Re-index Graph** button in Advanced Settings

### ✅ Content Display & Editing
- [x] JournalsView with reactive multi-journal display
- [x] BlockRenderer with wiki link, block ref, and tag support
- [x] **Atomic Merge/Split**: Backspace and Enter handle hierarchy correctly in DB.
- [x] Page Alias support indexed in SQL.

---

## Known Issues

| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| BUG-003 | Medium | JS/iOS targets disabled | Open |
| DB-001 | High | Integer PKs incompatible with replication/merge | Planned ([plan](docs/tasks/uuid-native-block-storage.md)) |
| - | Low | ClickableText deprecated API | Warning only |
| - | Low | expect/actual beta warnings | Cosmetic |
| MG-001 | Medium | Single-graph hardcoded; no multi-graph support | Planned ([plan](docs/tasks/multi-graph-support.md)) |

---

## Build & Test Commands

```bash
# Run the desktop application
./gradlew :kmp:runApp

# Compile JVM code
./gradlew :kmp:compileKotlinJvm

# Run tests
./gradlew :kmp:jvmTest
```
