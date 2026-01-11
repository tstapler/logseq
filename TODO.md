# TODO.md - Logseq Kotlin Compose Desktop Re-implementation

## Current Status
- **Migration State**: Build Stabilization Phase - Fixing KMP build failures
- **Technology Stack**: Kotlin 2.0.21, Compose Desktop 1.7.1, SQLDelight 2.0.2
- **Recent Activity**: Added graph switching UI and performance monitoring dashboard
- **Last Updated**: January 11, 2026
- **Current Focus**: KMP Build Failure Resolution (Story 1: Critical Android Build Success)

## Known Issues
- **Android & JS Targets**: Currently disabled due to build configuration issues (see [BUG-003](docs/bugs/open/003-android-js-targets-disabled.md)).
- **Skiko Incompatibility**: Potential runtime issues on some Linux environments due to dependency resolution.
- **Missing Platform Implementations**: Android target fails to compile due to missing `expect` implementations.

## Project Structure (Multiplatform)

```
kmp/
├── build.gradle.kts           # Kotlin Multiplatform configuration
└── src/
    ├── commonMain/
    │   └── kotlin/com/logseq/kmp/
    │       ├── platform/PlatformFileSystem.kt  # expect class
    │       ├── model/Models.kt                 # Page, Block, Property
    │       ├── repository/                     # Repository interfaces
    │       └── cache/                          # Caching layer
    ├── jvmMain/
    │   └── kotlin/com/logseq/kmp/
    │       ├── desktop/
    │       │   ├── Main.kt                     # Desktop entry point
    │       │   └── ui/
    │       │       ├── App.kt                  # Main Compose UI
    │       │       └── theme/Theme.kt          # Material3 theming
    │       ├── platform/PlatformFileSystem.kt  # actual JVM implementation
    │       └── repository/                     # JVM implementations
    ├── androidMain/
    │   └── kotlin/com/logseq/kmp/
    │       └── platform/PlatformFileSystem.kt  # actual Android implementation
    ├── iosMain/
    │   └── kotlin/com/logseq/kmp/
    │       └── platform/PlatformFileSystem.kt  # actual iOS implementation
    └── jsMain/
        └── kotlin/com/logseq/kmp/
            └── platform/PlatformFileSystem.kt  # actual JS implementation
```

## Completed Work

### ✅ Kotlin Multiplatform Restructure
- Converted from plain JVM project to proper multiplatform
- Configured jvm, js targets with Compose support
- Proper expect/actual pattern for PlatformFileSystem

### ✅ PlatformFileSystem Implementation
- `readFile(path: String): String?` - Read file contents
- `writeFile(path: String, content: String): Boolean` - Write file contents
- `listFiles(path: String): List<String>` - List directory contents
- `listDirectories(path: String): List<String>` - List subdirectories
- `fileExists(path: String): Boolean` - Check file existence
- `directoryExists(path: String): Boolean` - Check directory existence
- `createDirectory(path: String): Boolean` - Create directory
- `deleteFile(path: String): Boolean` - Delete file
- `expandTilde(path: String): String` - Convert ~ to home directory
- `getDefaultGraphPath(): String` - Get default graph location
- Security validation prevents path traversal

### ✅ Compose Desktop UI
- Main application window with menu bar
- Theme toggle (Light/Dark/System) via View dropdown
- Clickable sidebar with Favorites and Recent pages
- Page content display area
- Visual selection indicators
- Graph location display (~/Documents/logseq)
- File menu with "Switch Graph" option
- Demo graph loading button for onboarding

### ✅ Performance Monitoring
- Performance monitor infrastructure for tracking operation timing
- Performance dashboard UI component for visualizing metrics
- Instrumentation of critical paths (graph loading, rendering)

---

## Active Development

### 🎯 KMP BUILD FAILURE RESOLUTION (Build Stabilization)
*See [docs/tasks/kmp-build-failure-resolution.md](docs/tasks/kmp-build-failure-resolution.md) for detailed plan*

#### Story 1: Critical Android Build Success (In Progress)
- [ ] Task 1.1: Android Platform Implementation (2h) - *Recommended Next Step*
- [ ] Task 1.2: Missing Dependencies Resolution (1h)
- [ ] Task 1.3: Repository Interface Alignment (2h)
- [ ] Task 1.4: StateFlow Reactive Pattern Fixes (1h)

#### Story 2: Platform Target Re-enablement (Planned)
- [ ] Task 2.1: JS Target Node.js Resolution
- [ ] Task 2.2: iOS Target Ivy Repository Fix
- [ ] Task 2.3: Cross-Platform Integration Testing

### ⏸️ GRAPH LOADING IMPLEMENTATION (Paused)

#### Story: Startup Performance & Debugging (COMPLETED)
- [x] Task 1.1: Performance Monitor Infrastructure
- [x] Task 1.2: Instrument GraphLoader
- [x] Task 2.1: Performance Dashboard UI

#### Story 1: FileSystem Enhancements (COMPLETED)
- [x] Task 1.1: Add File Reading to PlatformFileSystem
- [x] Task 1.2: Implement JVM File Operations
- [x] Task 1.3: Implement Mobile/JS File Operations

#### Story 2: Markdown Parser (Not Started)
- [ ] Task 2.1: Define Markdown Parser Interface
- [ ] Task 2.2: Implement Logseq Markdown Parser
- [ ] Task 2.3: Add Parser Tests

---

### UI Features (In Progress)

#### Navigation & Pages
- [x] Connect sidebar to actual PageRepository data
- [ ] Add page create/delete functionality
- [ ] Implement page editing view
- [ ] Add breadcrumbs for namespace navigation

#### Keyboard Shortcuts
- [ ] Ctrl+K - Search/command palette
- [ ] Ctrl+N - Create new page
- [ ] Ctrl+B - Toggle sidebar
- [ ] Ctrl+S - Save page

#### Command Palette
- [ ] Design command palette UI
- [ ] Implement search functionality
- [ ] Add commands: create page, goto page, toggle theme, etc.

---

## Project Status Summary

| Metric | Value | Status |
|--------|-------|--------|
| Desktop App | Running | ✅ Working |
| Theme Toggle | Implemented | ✅ Done |
| Sidebar Navigation | Connected to data | ✅ Done |
| PlatformFileSystem | Fully implemented | ✅ Done |
| Performance Monitoring | Dashboard complete | ✅ Done |
| Graph Switching | UI implemented | ✅ Done |
| **Build Stabilization** | **Story 1 Started** | ⚠️ In Progress |
| Graph Loading | Story 1 complete | ⏸️ Paused |
| Command Palette | Not Started | ❌ Pending |
| Block Editor | Not Started | ❌ Pending |

---

## Build & Test Commands

```bash
# Run the desktop application
./gradlew :kmp:runApp

# Compile JVM code
./gradlew :kmp:compileKotlinJvm

# Compile all targets
./gradlew :kmp:compileKotlin

# List available tasks
./gradlew :kmp:tasks
```

---

## Next Steps

1. **Story 1: Critical Android Build Success** (6 hours) - [Details](docs/tasks/kmp-build-failure-resolution.md)
   - **Task 1.1: Android Platform Implementation** (2h) - *Recommended Next Step*
     - Create missing Android platform implementations for `Time.kt` and others
     - Resolve `expect`/`actual` mismatches
   - **Task 1.2: Missing Dependencies Resolution** (1h)
     - Add missing dependencies to `build.gradle.kts`
   - **Task 1.3: Repository Interface Alignment** (2h)
     - Extend repository interfaces to match `EditorViewModel` usage

2. **Story 2: Platform Target Re-enablement** (2 weeks)
   - Fix JS and iOS build configurations

---

*Last Updated: January 11, 2026*
*Framework: Kotlin Multiplatform 2.0.21 with Compose Desktop 1.7.1*
