# TODO.md - Logseq Kotlin Compose Desktop Re-implementation

## Current Status
- **Migration State**: Feature Implementation Phase - Core features complete!
- **Technology Stack**: Kotlin 2.0.21, Compose Multiplatform 1.7.1, **SQLDelight 2.0.2 (Persistent)**
- **Recent Activity**: Migrated to SQLDelight, fixed hierarchy data integrity bugs, extracted MarkdownEngine.
- **Last Updated**: March 22, 2026
- **Current Focus**: Editing System Remediation

## Build Status

| Target | Status | Notes |
|--------|--------|-------|
| JVM/Desktop | ✅ PASSING | Stable, SQLDelight persistent |
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

### P0: ARCHITECTURE & MAINTAINABILITY
- [ ] **[UI-001] Decompose BlockRenderer** - Split 600+ line God Object into Gutter, Editor, and Viewer components.
- [ ] **[ED-001] Implement Undo/Redo Command Pattern** - Replace log-only stubs with full command-pattern implementation for all operations.
- [ ] **[ED-002] Decouple UI from Editor Core** - Remove `androidx.compose` dependencies from `editor` and `model` packages.
- [ ] **[TEST-001] Fix Flaky Test Sync** - Replace `delay(50)` in ViewModel tests with proper coroutine test dispatchers.

### P0: DATA ARCHITECTURE (Replication & Merge Readiness)
- [ ] **[DB-001] UUID-Native Block Storage** - Migrate from INTEGER AUTOINCREMENT PKs to UUID TEXT PKs across all tables. Enables cross-device merge, content deduplication, and replication support. **Plan**: [`docs/tasks/uuid-native-block-storage.md`](docs/tasks/uuid-native-block-storage.md)
  - [ ] Phase 1: Schema migration (UUID PKs, FTS5 compat, query rewrites, migration script)
  - [ ] Phase 2: Model & repository layer (remove `id: Long`, UUID-only identity)
  - [ ] Phase 3: GraphLoader UUID-native loading (remove epoch-ms IDs, populate `left_uuid`, content hashing)
  - [ ] Phase 4: Content deduplication queries
  - [ ] Phase 5: CRDT-ready infrastructure (device ID, HLC timestamps, soft deletes)
  - [ ] Phase 6: Test suite & migration validation

### P1: FEATURE COMPLETION
- [ ] **[OPS-001] Implement Subtree Operations** - `promoteSubtree`, `demoteSubtree`, and `duplicateSubtree` are currently stubs.
- [ ] **[FTS-001] Native Search Optimization** - Migrate search logic from Kotlin-filtering to native SQLite FTS5 using `searchBlocksByContentFts`.
- [ ] **[MG-001] Multi-Graph Support** - Allow users to manage multiple knowledge graphs with per-graph SQLite databases. **Plan**: [`docs/tasks/multi-graph-support.md`](docs/tasks/multi-graph-support.md)
  - [ ] Phase 1: Foundation (hashing, GraphInfo model, canonicalizePath, databaseUrlForGraph)
  - [ ] Phase 2: Repository lifecycle (GraphManager, driver close/open, StateFlow<RepositorySet>)
  - [ ] Phase 3: ViewModel + UI integration (graph switcher, key-scoped ViewModels)
  - [ ] Phase 4: Migration + polish (single-DB migration, remove graph, status bar)

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
