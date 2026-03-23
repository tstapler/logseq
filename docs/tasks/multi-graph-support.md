# Multi-Graph Support

## Summary

Enable users to manage multiple Logseq knowledge graphs (e.g., `~/wiki`, `~/work-notes`) simultaneously, each backed by its own SQLite database. The active graph determines which repositories, DB connection, and `GraphLoader` instance are in use. A persistent graph list and "last active graph" setting survive app restarts.

## Current Architecture (Single Graph)

Today the app is hard-wired to a single database:

1. **`defaultDatabaseUrl`** (platform `expect/actual` in `DriverFactory.kt` / `DriverFactory.jvm.kt`) returns a fixed path like `~/.local/share/logseq/logseq.db`.
2. **`Repositories` singleton** (`RepositoryFactory.kt`) calls `Repositories.initialize(DriverFactory(), defaultDatabaseUrl)` once. All repositories share the single `LogseqDatabase` instance created by that call.
3. **`RepositoryFactoryImpl`** lazily creates the `LogseqDatabase` and caches repository instances by key (e.g., `"block_sqldelight"`). There is no mechanism to swap the underlying database.
4. **`GraphLoader`** receives `PageRepository` and `BlockRepository` via constructor injection -- these are the singleton instances from step 2.
5. **`LogseqApp` composable** calls `Repositories.initialize(...)` inside a `remember` block. It then creates `LogseqViewModel`, `JournalsViewModel`, etc., all pinned to those repositories.
6. **`AppState`** holds `currentGraphPath: String` but this only controls which filesystem directory `GraphLoader` reads markdown from; the DB is always the same file.
7. **`PlatformSettings`** persists `lastGraphPath` as a simple string -- no concept of a graph list.

### Key Files

| File | Role |
|------|------|
| `kmp/src/commonMain/kotlin/com/logseq/kmp/db/DriverFactory.kt` | `expect` class + `createDatabase()` helper |
| `kmp/src/jvmMain/kotlin/com/logseq/kmp/db/DriverFactory.jvm.kt` | `actual` with `defaultDatabaseUrl` |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/repository/RepositoryFactory.kt` | `RepositoryFactoryImpl`, `Repositories` singleton, `RepositorySet` |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/db/GraphLoader.kt` | Reads markdown from disk, writes to repositories |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/ui/App.kt` | Top-level composable, creates ViewModels |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/ui/AppState.kt` | `AppState` data class, `Screen` sealed class |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/ui/LogseqViewModel.kt` | Main ViewModel, holds `currentGraphPath` |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/ui/screens/JournalsViewModel.kt` | Journal-specific ViewModel |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/platform/PlatformSettings.kt` | Key-value persistence |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/platform/FileSystem.kt` | Filesystem abstraction |
| `kmp/src/commonMain/sqldelight/com/logseq/kmp/db/LogseqDatabase.sq` | Schema (pages, blocks, etc.) |

---

## Requirements

### Functional Requirements

| ID | Requirement | Priority |
|----|-------------|----------|
| MG-FR-01 | User can add a graph by selecting a directory via the existing `pickDirectory()` mechanism. | Must |
| MG-FR-02 | User can view a list of all registered graphs and select one to activate. | Must |
| MG-FR-03 | Switching graphs replaces the active DB connection and repositories; the UI resets to the Journals screen. | Must |
| MG-FR-04 | Each graph has its own SQLite database file, isolated from other graphs. | Must |
| MG-FR-05 | The list of registered graphs persists across app restarts. | Must |
| MG-FR-06 | The last-active graph is restored on startup. | Must |
| MG-FR-07 | User can remove a graph from the list (does not delete the graph directory or its DB file). | Should |
| MG-FR-08 | User can rename the display name of a graph. | Could |
| MG-FR-09 | The status bar shows the active graph name. | Should |
| MG-FR-10 | Re-index operates on the active graph only. | Must |

### Non-Functional Requirements

| ID | Requirement |
|----|-------------|
| MG-NFR-01 | Switching graphs completes in under 2 seconds for an already-loaded graph (warm DB). |
| MG-NFR-02 | Only the active graph's DB connection is open at a time; idle graph connections are closed. |
| MG-NFR-03 | DB filenames are deterministic given a graph path -- renaming the same path always yields the same file. |
| MG-NFR-04 | Existing single-graph users are migrated transparently on first launch after upgrade. |

---

## Architecture Design

### ADR-001: Per-Graph Database via RepositoryFactory Re-initialization

**Context**: The current `Repositories` singleton initializes once. We need the ability to swap the backing database.

**Decision**: Replace the single-shot `Repositories.initialize()` with a `switchGraph(graphId)` method that:
1. Closes the current `SqlDriver` (if any).
2. Computes the new JDBC URL from the graph path.
3. Creates a new `DriverFactory` + `LogseqDatabase` + repositories.
4. Exposes the new repositories to all consumers via `StateFlow<RepositorySet>`.

**Consequences**:
- ViewModels must observe the active `RepositorySet` rather than holding a permanent reference to a single repository instance.
- `GraphLoader` must be re-created (or accept new repositories) on each switch.
- The `Repositories` singleton evolves into a `GraphManager` that owns the lifecycle.

**Alternatives Rejected**:
- *Keep all graph DBs open simultaneously*: Wasteful on mobile, increases memory footprint, risk of file handle exhaustion with many graphs.
- *Single DB with a `graph_id` column on every table*: Requires invasive schema change, complicates FTS triggers, breaks the clean isolation model, makes per-graph backup/deletion harder.

### ADR-002: Database File Naming

**Context**: Each graph needs a unique, stable SQLite filename.

**Decision**: Derive the filename from a SHA-256 hash of the **canonical absolute path** of the graph directory.

```
logseq-graph-{sha256(canonicalPath).take(16)}.db
```

Examples:
- `/home/user/wiki` --> `logseq-graph-a3f1b2c4d5e6f7a8.db`
- `/home/user/work-notes` --> `logseq-graph-9b8c7d6e5f4a3b2c.db`

The DB files live in the platform's app data directory (same location as the current `logseq.db`).

**Rationale**:
- Deterministic: same path always yields same hash.
- No special characters or length issues from using the path itself as a filename.
- 16 hex chars (64 bits) gives negligible collision probability for a human-scale number of graphs.
- The full path-to-hash mapping is stored in the graph registry (PlatformSettings), so the hash is verifiable.

**Alternatives Rejected**:
- *User-provided name*: Can conflict, requires sanitization, breaks on rename.
- *Full path as filename*: Slashes, colons, and length limits make this non-portable.
- *Sequential integer IDs*: Not deterministic from path alone; re-adding a removed graph creates a new DB.

### ADR-003: Graph Registry Storage

**Context**: We need to persist the list of graphs and which one is active.

**Decision**: Store graph metadata as a JSON string in `PlatformSettings` under the key `"graph_registry"`. The JSON schema:

```json
{
  "activeGraphId": "a3f1b2c4d5e6f7a8",
  "graphs": [
    {
      "id": "a3f1b2c4d5e6f7a8",
      "path": "/home/user/wiki",
      "displayName": "Wiki",
      "addedAt": 1711152000000
    },
    {
      "id": "9b8c7d6e5f4a3b2c",
      "path": "/home/user/work-notes",
      "displayName": "Work Notes",
      "addedAt": 1711238400000
    }
  ]
}
```

The `id` field is the first 16 hex chars of `sha256(path)`, matching the DB filename.

**Rationale**: `PlatformSettings` already works cross-platform. A dedicated SQLite "meta-database" would add complexity for a small amount of data. JSON serialization is straightforward with `kotlinx.serialization`.

---

## Detailed Design

### 1. Domain Model

```kotlin
// New file: kmp/src/commonMain/kotlin/com/logseq/kmp/model/GraphInfo.kt

@Serializable
data class GraphInfo(
    val id: String,           // sha256(canonicalPath).take(16)
    val path: String,         // Canonical absolute path
    val displayName: String,  // User-facing name (defaults to directory name)
    val addedAt: Long         // Epoch millis
)

@Serializable
data class GraphRegistry(
    val activeGraphId: String? = null,
    val graphs: List<GraphInfo> = emptyList()
)
```

### 2. GraphManager (Replaces Repositories Singleton)

```kotlin
// New file: kmp/src/commonMain/kotlin/com/logseq/kmp/db/GraphManager.kt
```

**Responsibilities**:
- Owns the `GraphRegistry` (load/save to `PlatformSettings`).
- Computes DB URLs from graph paths.
- Creates / closes `SqlDriver` and `LogseqDatabase` instances.
- Exposes `StateFlow<RepositorySet?>` for the active graph.
- Exposes `StateFlow<GraphRegistry>` for UI to render the graph list.
- Provides `addGraph(path)`, `removeGraph(id)`, `switchGraph(id)`, `renameGraph(id, newName)`.

**Key method -- switchGraph**:

```
1. Save any pending writes (flush debounce buffers).
2. Close current SqlDriver via driver.close().
3. Look up new graph in registry.
4. Compute JDBC URL: "jdbc:sqlite:{appDataDir}/logseq-graph-{id}.db"
5. Create new DriverFactory + driver + LogseqDatabase.
6. Create new RepositorySet (block, page, property, reference, search).
7. Create new GraphLoader with new repositories.
8. Emit new RepositorySet via StateFlow.
9. Update registry's activeGraphId. Persist.
10. Trigger graph load if DB is empty (first open).
```

### 3. Database URL Computation

Add a new function alongside `defaultDatabaseUrl`:

```kotlin
// In DriverFactory.kt (common)
fun databaseUrlForGraph(graphId: String): String

// In DriverFactory.jvm.kt (actual)
// Returns: "jdbc:sqlite:{basePath}/logseq-graph-{graphId}.db"
// where basePath is the same platform-specific directory used by defaultDatabaseUrl
```

The hash computation uses a common `expect/actual` for SHA-256, or a pure-Kotlin implementation (the `kotlinx-io` or a small utility).

### 4. AppState Changes

```kotlin
data class AppState(
    // ... existing fields ...
    val currentGraphPath: String = "",
    val currentGraphId: String? = null,         // NEW
    val currentGraphName: String = "",           // NEW
    val availableGraphs: List<GraphInfo> = emptyList(), // NEW
    val isGraphSwitching: Boolean = false,       // NEW - loading indicator during switch
)
```

### 5. LogseqApp Composable Changes

The `LogseqApp` composable currently creates repositories in a `remember` block. This must change:

1. `GraphManager` is created once and passed down (or created in `remember`).
2. `GraphManager.activeRepositorySet` is collected as state.
3. When `activeRepositorySet` changes (graph switch), ViewModels are recreated using `key(graphId)` composable scoping.
4. The `graphId` serves as the `key` for `remember` blocks that create ViewModels, ensuring they are discarded and recreated on graph switch.

```kotlin
val graphManager = remember { GraphManager(platformSettings, DriverFactory()) }
val activeRepoSet by graphManager.activeRepositorySet.collectAsState()
val graphRegistry by graphManager.graphRegistry.collectAsState()
val activeGraphId = graphRegistry.activeGraphId

// Re-key ViewModels when graph changes
key(activeGraphId) {
    val repos = activeRepoSet ?: return@key // Show loading
    val graphLoader = remember { GraphLoader(fileSystem, repos.pageRepository, repos.blockRepository) }
    val viewModel = remember { LogseqViewModel(..., repos.pageRepository, repos.blockRepository, ...) }
    val journalsViewModel = remember { JournalsViewModel(repos.pageRepository, repos.blockRepository, graphLoader, scope) }
    // ... rest of UI
}
```

### 6. UI: Graph Switcher

Add a graph picker to the left sidebar or top bar:

- **Graph dropdown** in the left sidebar header showing the current graph name.
- Clicking opens a dropdown/dialog listing all registered graphs.
- "Add graph" button triggers `pickDirectory()` flow.
- "Remove graph" via long-press or context menu.
- Active graph is highlighted.

### 7. Migration Strategy (Existing Single-DB Users)

On first launch after upgrade:

1. `GraphManager.initialize()` checks if `graph_registry` key exists in `PlatformSettings`.
2. If absent, check for `lastGraphPath` (existing setting).
3. If `lastGraphPath` exists:
   a. Compute `graphId = sha256(lastGraphPath).take(16)`.
   b. Check if old `logseq.db` exists at the default location.
   c. **Rename** `logseq.db` to `logseq-graph-{graphId}.db` (atomic file rename).
   d. Create the `GraphRegistry` with this single graph as active.
   e. Persist to `PlatformSettings`.
4. If `lastGraphPath` is empty (fresh install or never completed onboarding), do nothing -- onboarding will handle it.

This migration is a one-time operation. The old `logseq.db` filename is never used again.

### 8. Hash Computation

For SHA-256 in common code:

- Option A: Use `kotlinx-io` hashing utilities if available.
- Option B: Add a small pure-Kotlin SHA-256 (no external dependency, ~100 lines).
- Option C: Use `expect/actual` -- JVM uses `java.security.MessageDigest`, Android same, iOS uses `CommonCrypto`.

Recommendation: Option C (`expect/actual`) since the codebase already uses this pattern extensively and SHA-256 is available natively on all target platforms.

```kotlin
// kmp/src/commonMain/kotlin/com/logseq/kmp/util/Hashing.kt
expect fun sha256Hex(input: String): String

// kmp/src/jvmMain/kotlin/com/logseq/kmp/util/Hashing.jvm.kt
actual fun sha256Hex(input: String): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}
```

Graph ID derivation:
```kotlin
fun graphIdFromPath(canonicalPath: String): String = sha256Hex(canonicalPath).take(16)
```

---

## Known Issues

### BUG: Race Condition During Graph Switch [SEVERITY: High]

**Description**: If a background `GraphLoader` coroutine is still writing to the old DB when `switchGraph()` closes the `SqlDriver`, the write will fail with a "database is closed" exception.

**Mitigation**:
- `GraphManager.switchGraph()` must cancel any in-flight `GraphLoader` coroutine scope before closing the driver.
- Use a `SupervisorJob` per graph session and cancel it during switch.
- Add a brief grace period (100ms) between cancellation and driver close.
- Wrap driver close in try-catch to handle edge cases.

**Files Likely Affected**: `GraphManager.kt`, `GraphLoader.kt`, `LogseqViewModel.kt`

### BUG: Stale Repository References in ViewModels [SEVERITY: High]

**Description**: If a ViewModel captures a `PageRepository` or `BlockRepository` reference at creation time and the graph switches, the ViewModel continues using the old (now-closed) repository.

**Mitigation**:
- Use Compose `key(graphId)` to force ViewModel recreation on graph switch.
- Never cache repository references outside the keyed scope.
- Consider making repositories in ViewModels `by lazy` from a provider rather than constructor-injected.

**Files Likely Affected**: `LogseqViewModel.kt`, `JournalsViewModel.kt`, `App.kt`

### BUG: Migration File Rename Failure [SEVERITY: Medium]

**Description**: On some platforms, renaming `logseq.db` may fail if the file is locked by another process or if WAL/SHM files exist alongside it.

**Mitigation**:
- Close any open driver before attempting rename.
- Also rename `logseq.db-wal` and `logseq.db-shm` if they exist.
- If rename fails, fall back to copy-then-delete.
- Log the migration outcome for debugging.

**Files Likely Affected**: `GraphManager.kt`, `DriverFactory.jvm.kt`

### BUG: Path Canonicalization Inconsistencies [SEVERITY: Medium]

**Description**: The same directory can be referred to by different paths (e.g., `~/wiki` vs `/home/user/wiki` vs `/home/user/wiki/`). If not canonicalized, the same graph gets different IDs and separate DBs.

**Mitigation**:
- Always canonicalize paths before computing the graph ID: resolve symlinks, expand `~`, remove trailing slashes.
- Use `FileSystem.expandTilde()` (already exists) plus `java.io.File.canonicalPath` (JVM) or equivalent.
- Add `expect fun canonicalizePath(path: String): String` to the platform abstraction.

**Files Likely Affected**: `GraphManager.kt`, `FileSystem.kt`, platform implementations

### BUG: Concurrent PlatformSettings Writes [SEVERITY: Low]

**Description**: If graph registry is updated from multiple coroutines simultaneously (unlikely but possible during rapid switch-add sequences), JSON could be corrupted.

**Mitigation**:
- Protect `GraphRegistry` read/write behind a `Mutex` in `GraphManager`.
- `PlatformSettings` writes are already synchronous per platform, but the read-modify-write cycle is not atomic.

**Files Likely Affected**: `GraphManager.kt`

### BUG: FTS5 State After Driver Swap [SEVERITY: Low]

**Description**: The FTS5 virtual table and its triggers are per-database. This is not a bug per se, but worth noting: each graph DB independently maintains its own FTS index. No cross-graph search is possible.

**Mitigation**: None required -- this is the expected behavior. Document that search is per-graph.

---

## Implementation Plan

### Phase 1: Foundation (Core Infrastructure)

- [ ] **1.1** Add `sha256Hex` expect/actual utility (`util/Hashing.kt`).
- [ ] **1.2** Add `GraphInfo` and `GraphRegistry` data classes with `kotlinx.serialization` (`model/GraphInfo.kt`).
- [ ] **1.3** Add `canonicalizePath` to `FileSystem` interface and platform implementations.
- [ ] **1.4** Add `databaseUrlForGraph(graphId: String)` to `DriverFactory` expect/actual.
- [ ] **1.5** Implement `GraphManager` class with `addGraph`, `removeGraph`, `switchGraph`, registry persistence.
- [ ] **1.6** Unit tests for graph ID derivation, path canonicalization, registry serialization.

**Dependency**: None. Can start immediately.

### Phase 2: Repository Lifecycle (Wire GraphManager to Repositories)

- [ ] **2.1** Deprecate `Repositories` singleton. Make `GraphManager` the sole owner of `RepositorySet` creation.
- [ ] **2.2** Expose `StateFlow<RepositorySet?>` from `GraphManager`.
- [ ] **2.3** Implement driver close/open cycle in `switchGraph` with coroutine cancellation.
- [ ] **2.4** Integration test: create two graphs, switch between them, verify data isolation.

**Dependency**: Phase 1.

### Phase 3: ViewModel + UI Integration

- [ ] **3.1** Refactor `LogseqApp` to use `GraphManager` instead of `Repositories.initialize()`.
- [ ] **3.2** Add `key(graphId)` scoping for ViewModel creation in `LogseqApp`.
- [ ] **3.3** Update `AppState` with graph-related fields.
- [ ] **3.4** Update `LogseqViewModel` to delegate graph operations to `GraphManager`.
- [ ] **3.5** Add graph switcher UI component (dropdown in left sidebar).
- [ ] **3.6** Wire "Add graph" to `pickDirectory()` + `GraphManager.addGraph()`.

**Dependency**: Phase 2.

### Phase 4: Migration + Polish

- [ ] **4.1** Implement single-DB migration logic in `GraphManager.initialize()`.
- [ ] **4.2** Handle WAL/SHM file migration.
- [ ] **4.3** Show active graph name in status bar.
- [ ] **4.4** Add "Remove graph" UI flow with confirmation dialog.
- [ ] **4.5** End-to-end test: fresh install, upgrade from single-graph, multi-graph workflow.

**Dependency**: Phase 3.

---

## Testing Strategy

| Level | Scope | Key Scenarios |
|-------|-------|---------------|
| Unit | `graphIdFromPath` | Deterministic output, path normalization, edge cases (empty, Unicode, spaces) |
| Unit | `GraphRegistry` serialization | Round-trip JSON, empty registry, missing fields (forward compat) |
| Unit | `GraphManager` | Add/remove/switch graphs, duplicate path detection, active graph persistence |
| Integration | Repository isolation | Write to graph A, switch to graph B, verify B is empty, switch back, verify A data intact |
| Integration | Migration | Seed a `logseq.db`, run migration, verify data accessible under new filename |
| Integration | Driver lifecycle | Switch graphs rapidly, verify no "database is closed" exceptions |
| UI | Graph switcher | Add graph, switch, verify Journals view reloads, verify status bar updates |

---

## Files to Create

| File | Purpose |
|------|---------|
| `kmp/src/commonMain/kotlin/com/logseq/kmp/model/GraphInfo.kt` | `GraphInfo`, `GraphRegistry` data classes |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/db/GraphManager.kt` | Central graph lifecycle manager |
| `kmp/src/commonMain/kotlin/com/logseq/kmp/util/Hashing.kt` | `expect fun sha256Hex(input: String): String` |
| `kmp/src/jvmMain/kotlin/com/logseq/kmp/util/Hashing.jvm.kt` | JVM actual for SHA-256 |
| `kmp/src/androidMain/kotlin/com/logseq/kmp/util/Hashing.android.kt` | Android actual (same as JVM) |

## Files to Modify

| File | Change |
|------|--------|
| `RepositoryFactory.kt` | Deprecate `Repositories` singleton; `RepositoryFactoryImpl` gains a `close()` method |
| `DriverFactory.kt` | Add `databaseUrlForGraph(graphId)` expect declaration |
| `DriverFactory.jvm.kt` | Add actual `databaseUrlForGraph`, return driver from `createDriver` for lifecycle mgmt |
| `FileSystem.kt` | Add `canonicalizePath(path: String): String` |
| `AppState.kt` | Add `currentGraphId`, `currentGraphName`, `availableGraphs`, `isGraphSwitching` |
| `App.kt` | Replace `Repositories.initialize()` with `GraphManager`; add `key(graphId)` scoping |
| `LogseqViewModel.kt` | Delegate `setGraphPath` / `loadGraph` to `GraphManager.switchGraph` |
| `PlatformFileSystem` (JVM/Android) | Implement `canonicalizePath` |
