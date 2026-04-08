# TODO.md - Logseq Kotlin Compose Desktop Re-implementation

## Current Status
- **Migration State**: Feature Implementation Phase - Core features complete!
- **Technology Stack**: Kotlin 2.0.21, Compose Multiplatform 1.7.1, **SQLDelight 2.1.0 (Persistent)**
- **Recent Activity**: Migrated to SQLDelight, fixed hierarchy data integrity bugs, extracted MarkdownEngine.
- **Last Updated**: April 4, 2026
- **Current Focus**: Stability, Progressive Loading, and Advanced Search

## Build Status

| Target | Status | Notes |
|--------|--------|-------|
| JVM/Desktop | ✅ PASSING | Stable, SQLDelight persistent, Skiko resolved transitively (0.8.18) |
| Android | ✅ PASSING | Stable, SQLDelight persistent |
| JS | ✅ PASSING | Fixed via BUG-003, SQLDelight web-worker-driver + SQL.js |
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
    │       ├── db/                # GraphLoader, DriverFactory, GraphManager
    │       ├── editor/            # Editor core logic
    │       └── ui/                # Compose UI components
    ├── jvmMain/                   # JVM/Desktop implementations
    └── androidMain/               # Android implementations
```

---

## Active Remediation (Post-Review March 2026)

### P0: STABILITY & COMPATIBILITY
- [x] **[JVM-001] Fix Skiko Runtime Crash** - Resolved transitively via Compose 1.7.1 on Kotlin 2.0.21.
- [x] **[BUG-003] Fix JS/Android Target Issues** - Re-enabled targets with proper driver initialization and memory settings.

### P0: ARCHITECTURE & MAINTAINABILITY
- [x] **[UI-001] Decompose BlockRenderer** - Split 600+ line God Object into BlockGutter, BlockEditor, BlockViewer, BlockItem, BlockList components.
- [x] **[ED-001] Implement Undo/Redo Command Pattern** - Undo/redo stack in JournalsViewModel: lightweight content edits + full page snapshots for structural ops. Ctrl+Z/Ctrl+Shift+Z/Ctrl+Y wired in App.kt.
- [x] **[ED-002] Decouple UI from Editor Core** - Moved `AutocompleteMenu` from `editor.components` to `ui.components`; removed cross-package Compose dependency.
- [x] **[TEST-001] Fix Flaky Test Sync** - Replace `delay(50)` with `UnconfinedTestDispatcher(testScheduler)` + `backgroundScope` for deterministic, non-hanging tests.
- [x] **[DB-001] UUID-Native Block Storage** - Moved from numeric IDs to UUID-native storage across the entire application. Enables cross-device merge.
- [x] **[MG-001] Multi-Graph Support** - Allow users to manage multiple knowledge graphs with per-graph SQLite databases.

### P1: FEATURE COMPLETION
- [x] **[OPS-001] Implement Subtree Operations** - `promoteSubtree`, `demoteSubtree`, and `duplicateSubtree` are implemented.
- [x] **[FTS-001] Native Search Optimization** - `searchWithFilters` and `searchBlocksByContent` now use FTS5.
- [ ] **[PL-001] Progressive Data Loading** - Implement pagination for all pages and linked references. ([plan](docs/tasks/progressive-loading.md))
- [ ] **[SR-001] Advanced Search & Query** - Implement Datalog engine via Datascript. ([plan](docs/tasks/search-system.md))

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
- [x] UUID-Native storage
- [x] Multi-graph support (GraphManager)

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
| iOS-001 | Medium | iOS target disabled due to Ivy repository issues | Open |
| PL-001 | High | "All Pages" loads all pages into memory; linked refs unpaginated | Planned ([plan](docs/tasks/progressive-loading.md)) |
| SR-001 | High | Datalog engine (Datascript) not implemented | Planned ([plan](docs/tasks/search-system.md)) |
| - | Low | ClickableText deprecated API | Warning only |
| - | Low | expect/actual beta warnings | Cosmetic |

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
