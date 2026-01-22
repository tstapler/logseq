# TODO.md - Logseq Kotlin Compose Desktop Re-implementation

## Current Status
- **Migration State**: Feature Implementation Phase - Building Logseq feature parity
- **Technology Stack**: Kotlin 2.0.21, Compose Desktop 1.7.1, SQLDelight 2.0.2
- **Recent Activity**: Implemented journals view, wiki links, edit/view mode, content display
- **Last Updated**: January 21, 2026
- **Current Focus**: Feature Parity with Logseq

## Build Status

| Target | Status | Notes |
|--------|--------|-------|
| JVM/Desktop | ✅ BUILDS | Primary development target |
| Android | ✅ BUILDS | Warnings only (expect/actual beta) |
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
    │       ├── repository/        # Repository interfaces
    │       ├── db/                # GraphLoader, GraphWriter
    │       ├── editor/            # Editor components
    │       └── ui/                # Compose UI components
    ├── jvmMain/                   # JVM/Desktop implementations
    ├── androidMain/               # Android implementations
    ├── iosMain/                   # iOS implementations (disabled)
    └── jsMain/                    # JS implementations (disabled)
```

---

## Completed Work

### ✅ Build Stabilization (January 2026)
- [x] JVM target compiles and runs
- [x] Android target compiles successfully
- [x] Progressive startup loading (journals-first, ~500ms to interactive)
- [x] Java 17+ compatibility resolved

### ✅ Core Infrastructure
- [x] PlatformFileSystem with all file operations
- [x] GraphLoader with progressive loading
- [x] Block and Page repositories (in-memory)
- [x] Performance monitoring infrastructure

### ✅ UI Framework
- [x] Main application window with menu bar
- [x] Theme toggle (Light/Dark/System)
- [x] Left sidebar with navigation
- [x] Right sidebar (expandable)
- [x] Status bar with encryption indicator
- [x] Command palette infrastructure

### ✅ Content Display (January 21, 2026)
- [x] Block content loading from repository
- [x] Text state initialization for blocks
- [x] JournalsView with scrollable multi-journal display
- [x] BlockRenderer with wiki link support `[[Page Name]]`
- [x] Edit vs View mode for blocks
- [x] Click-to-edit functionality
- [x] Wiki link navigation to pages
- [x] Linked and Unlinked References Panel

---

## Active Development

### 🎯 FEATURE PARITY IMPLEMENTATION

#### Story 1: Block Editing (In Progress)
- [x] Task 1.1: Persist block edits to disk (2h)
- [x] Task 1.2: Auto-save with debouncing (1h)
- [ ] Task 1.3: Undo/redo support (2h)

#### Story 2: Block Hierarchy & Outliner (In Progress) - [View Plan](docs/tasks/block-hierarchy.md)
- [ ] Task 2.1: Tree structure visualization (2h)
- [ ] Task 2.2: Indent/outdent blocks (2h)
- [x] Task 2.3: Collapse/expand blocks (1h)
- [ ] Task 2.4: Drag-and-drop reordering (3h)

#### Story 3: Page Management (Planned) - [View Plan](docs/tasks/page-management.md)
- [x] Task 3.1: Create new page from wiki link (1h)
- [ ] Task 3.2: Delete page with confirmation (1h)
- [ ] Task 3.3: Rename page with reference updates (2h)
- [ ] Task 3.4: Page properties panel (2h)

#### Story 4: Search & Query System (Planned) - [View Plan](docs/tasks/search-system.md)
- [ ] Task 4.1: SQLite FTS5 Implementation (2h)
- [ ] Task 4.2: Datascript Query Engine (3h)
- [ ] Task 4.3: Search UI & Command Palette (3h)
- [ ] Task 4.4: Query Result Rendering (2h)

#### Story 5: Progressive Data Loading (Planned) - [View Plan](docs/tasks/progressive-loading.md)
- [ ] Task 5.1: Paginated Repository Methods (2h)
- [ ] Task 5.2: UI Infinite Scroll Integration (3h)
- [ ] Task 5.3: Lazy Reference Loading (2h)
- [ ] Task 5.4: Metadata-Only Initial Graph Load (3h)

### ⏸️ Platform Re-enablement (Blocked)
*See [BUG-003](docs/bugs/open/003-android-js-targets-disabled.md)*
- [ ] JS Target: Fix OutOfMemoryError and Node.js resolution
- [ ] iOS Target: Fix Ivy repository issues

---

## Known Issues

| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| BUG-003 | Medium | JS/iOS targets disabled | Open |
| - | Low | ClickableText deprecated API | Warning only |
| - | Low | expect/actual beta warnings | Cosmetic |

---

## Feature Gap Analysis (vs Logseq)

### High Priority (Core Functionality)
- [ ] Block editing persistence
- [ ] Block hierarchy/outliner display
- [ ] Create page from wiki link
- [ ] Backlinks panel
- [ ] Search functionality

### Medium Priority (User Experience)
- [ ] Keyboard shortcuts (Ctrl+K, Ctrl+N, etc.)
- [ ] Block references `((block-id))`
- [ ] Tags `#tag` support
- [ ] Page aliases
- [ ] Graph view

### Low Priority (Advanced Features)
- [ ] Flashcards/spaced repetition
- [ ] PDF annotation
- [ ] Whiteboards
- [ ] Plugins system
- [ ] Sync/collaboration

---

## Build & Test Commands

```bash
# Run the desktop application
./gradlew :kmp:runApp

# Compile JVM code
./gradlew :kmp:compileKotlinJvm

# Compile Android code
./gradlew :kmp:compileDebugKotlinAndroid

# Run tests
./gradlew :kmp:jvmTest
```

---

## Architecture Notes

### Data Flow
```
Markdown Files → GraphLoader → Repositories (In-Memory) → UI Components
                                    ↓
                              GraphWriter → Markdown Files
```

### Key Components
- **GraphLoader**: Reads markdown files, parses content, populates repositories
- **GraphWriter**: Persists changes back to markdown files
- **BlockRepository**: In-memory storage for blocks with reactive Flow
- **PageRepository**: In-memory storage for pages
- **BlockRenderer**: Renders blocks with wiki links and edit mode
- **JournalsView**: Displays multiple journals in scrollable list

---

*Last Updated: January 21, 2026*
*Framework: Kotlin Multiplatform 2.0.21 with Compose Desktop 1.7.1*

- [x] [KMP Markdown Parser Parity Plan](docs/tasks/kmp-markdown-parity.md)
