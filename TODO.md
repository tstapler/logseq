# TODO.md - Logseq Kotlin Compose Desktop Re-implementation

## Current Status
- **Migration State**: Feature Implementation Phase - Core features complete!
- **Technology Stack**: Kotlin 2.0.21, Compose Desktop 1.7.1, SQLDelight 2.0.2
- **Recent Activity**: Completed Stories 1-8: Tags and Block References implemented!
- **Last Updated**: February 1, 2026
- **Current Focus**: Polish and Advanced Features (Graph View)

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

#### Story 1: Block Editing ✅ Complete
- [x] Task 1.1: Persist block edits to disk (2h)
- [x] Task 1.2: Auto-save with debouncing (1h)
- [x] Task 1.3: Undo/redo support (2h) - Ctrl+Z / Ctrl+Shift+Z
- [x] Task 1.4: Block editing enhancements - Enter, Backspace, Split, Merge

#### Story 2: Block Hierarchy & Outliner (In Progress) - [View Plan](docs/tasks/block-hierarchy.md)
- [x] Task 2.1: Tree structure visualization (2h) - Vertical guide lines
- [x] Task 2.2: Indent/outdent blocks (2h) - Tab / Shift+Tab
- [x] Task 2.3: Collapse/expand blocks (1h)
- [x] Task 2.4: Drag-and-drop reordering (3h) - Basic drag implemented
- [x] Task 2.5: Mobile Block Toolbar (2h)
- [x] Task 2.6: Focus Navigation - Arrow keys between blocks

#### Story 3: Page Management ✅ Complete - [View Plan](docs/tasks/page-management.md)
- [x] Task 3.1: Create new page from wiki link (1h)
- [x] Task 3.2: Delete page with confirmation (1h)
- [x] Task 3.3: Rename page with reference updates (2h) - Updates all [[wiki links]]
- [x] Task 3.4: Page properties panel (2h) - Collapsible metadata panel
- [x] Task 3.5: Explicit Page Creation UI (2h) - Via search dialog (Ctrl+K)

#### Story 4: Search & Query System ✅ Complete - [View Plan](docs/tasks/search-system.md)
- [x] Task 4.1: Search Repository Implementation (2h) - InMemory + SQLDelight FTS5
- [x] Task 4.2: Search UI & Command Palette (3h) - Ctrl+K search dialog
- [x] Task 4.3: Query Result Rendering (2h) - Pages, blocks, create new page
- [x] Task 4.4: Relevance Scoring (1h) - Title match, content match weights

#### Story 5: Progressive Data Loading ✅ Complete - [View Plan](docs/tasks/progressive-loading.md)
- [x] Task 5.1: Paginated Repository Methods (2h)
- [x] Task 5.2: UI Infinite Scroll Integration (3h)
- [x] Task 5.3: Lazy Reference Loading (2h)
- [x] Task 5.4: Metadata-Only Initial Graph Load (3h)

#### Story 6: Android Readiness ✅ Complete - [View Plan](docs/tasks/android-readiness.md)
- [x] Task 6.1: Mobile Block Toolbar (2h) - Shows above keyboard when editing
- [x] Task 6.2: Touch-Friendly Drag & Drop (2h) - Drag handle with gesture
- [x] Task 6.3: Quick Capture / New Page UI (2h) - Floating action button

#### Story 7: Block References ✅ Complete - [View Plan](docs/tasks/block-references.md)
- [x] Task 7.1: Parser Support for ((uuid)) (1h)
- [x] Task 7.2: Repository Lookup by UUID (1h)
- [x] Task 7.3: UI Rendering of References (2h)

#### Story 8: Tags Support ✅ Complete - [View Plan](docs/tasks/tags-support.md)
- [x] Task 8.1: Parser Hardening for #tag (1h)
- [x] Task 8.2: UI Rendering and Interaction (1h)

### 🎯 COMPLETED TASKS
- [x] **Feat: Tags Support (#tag)** (Feb 1, 2026)
  - Parser: Hardened `InlineParser` to strict #tag validation
  - UI: Added regex rendering for tags in `BlockRenderer`
  - Interaction: Tags are clickable and navigate to the page
- [x] **Feat: Block References** (Feb 1, 2026)
  - Parser: Added `((uuid))` syntax support (Token, Lexer, InlineParser)
  - Data: Verified `getBlockByUuid` repository support
  - UI: Added `((uuid))` regex rendering with transclusion (fetches and displays referenced content)
  - Styling: Distinct italic/underline style for references
- [x] **Feat: Quick Capture FAB** (Jan 25, 2026)
  - Floating action button in bottom-right corner
  - Quick add to today's journal
  - New page creation shortcut
- [x] **Feat: Search & Query System** (Jan 25, 2026)
  - InMemorySearchRepository with relevance scoring
  - SearchDialog with page/block results
  - Create new page from search
  - Ctrl+K keyboard shortcut
- [x] **Feat: Page Properties Panel** (Jan 25, 2026)
  - Collapsible properties panel in PageView
  - Shows page metadata, dates, file path
  - User-defined properties from frontmatter
- [x] **Feat: Rename Page** (Jan 25, 2026)
  - Rename dialog with validation
  - Updates all [[wiki link]] references
  - File rename on disk
- [x] **Feat: Undo/Redo Support** (Jan 25, 2026)
  - Added Ctrl+Z / Ctrl+Shift+Z keyboard shortcuts
  - UndoManager with command pattern for all block operations
  - Automatic UI refresh after undo/redo
- [x] **Feat: Delete Page** (Jan 25, 2026)
  - Delete button with confirmation dialog in PageView
  - Removes file, blocks, and repository entry
  - Navigates away after deletion
- [x] **Feat: Tree Visualization** (Jan 25, 2026)
  - Added vertical guide lines for nested blocks
  - Visual hierarchy indicator
- [x] **Feat: Focus Navigation** (Jan 24, 2026)
  - Arrow Up/Down to move between blocks
  - Proper cursor positioning at line boundaries
- [x] **Feat: Block Editing Enhancements** (Jan 24, 2026)
  - Enter to create new block or split at cursor
  - Backspace to merge/delete blocks
  - Robust handling of edge cases
- [x] **Fix: GraphWriter Hierarchy Corruption (BUG-004)** (Jan 23, 2026)
  - Fixed sorting bug that corrupted block order on save
  - Implemented tree traversal instead of flat sorting
- [x] **Feat: Metadata-Only Initial Graph Load** (Jan 22, 2026)
  - Implemented Two-Phase Loading Strategy (Skeleton -> Full Content)
  - Added ParseMode to LogseqParser/MarkdownParser
  - Refactored GraphLoader to support progressive background loading
  - Updated UI to show "Loading..." placeholders for unloaded blocks
  - Fixed race conditions with file-level mutex
- [x] **Feat: Native KMP Graph Parser** (Jan 22, 2026)
  - Implemented high-performance, zero-copy Lexer and Parser in Kotlin
  - Replaced legacy `mldoc` (C++/WASM) dependency
  - Achieved feature parity for Logseq syntax (Indentation, Properties, Timestamps, Links)
  - Fixed block hierarchy and sorting issues
- [x] **Feat: Deterministic UUID Generation** (Jan 22, 2026)
  - Implemented stable UUIDs based on file path and content
  - Fixed duplicate block issues on reload
- [x] **Feat: Debug Mode** (Jan 22, 2026)
  - Added "Show Debug Info" toggle in View menu
  - Visualized block levels and structure for troubleshooting

### ⏸️ Platform Re-enablement (Blocked)
*See [BUG-003](docs/bugs/open/003-android-js-targets-disabled.md)*
- [ ] JS Target: Fix OutOfMemoryError and Node.js resolution
- [ ] iOS Target: Fix Ivy repository issues

---

## Known Issues

| ID | Severity | Description | Status |
|----|----------|-------------|--------|
| BUG-003 | Medium | JS/iOS targets disabled | Open |
| BUG-004 | Critical | GraphWriter corrupts block order | ✅ Fixed (Jan 23) |
| - | Low | ClickableText deprecated API | Warning only |
| - | Low | expect/actual beta warnings | Cosmetic |

---

## Feature Gap Analysis (vs Logseq)

### High Priority (Core Functionality) - ALL COMPLETE ✅
- [x] Block editing persistence ✅
- [x] Block hierarchy/outliner display ✅
- [x] Create page from wiki link ✅
- [x] Backlinks panel ✅
- [x] Search functionality ✅

### Medium Priority (User Experience)
- [x] Keyboard shortcuts (Ctrl+Z, Tab, Enter, Backspace, Ctrl+K, etc.) ✅
- [x] Block references `((block-id))` ✅
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

*Last Updated: January 25, 2026*
*Framework: Kotlin Multiplatform 2.0.21 with Compose Desktop 1.7.1*

- [x] [KMP Markdown Parser Parity Plan](docs/tasks/kmp-markdown-parity.md)
