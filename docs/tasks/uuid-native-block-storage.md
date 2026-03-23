# Feature Plan: UUID-Native Block Storage for Replication & Merge

## Epic Overview

**Problem Statement**: The current schema uses `INTEGER PRIMARY KEY AUTOINCREMENT` for `blocks.id` and `pages.id`, with all foreign key relationships (`parent_id`, `left_id`, `page_id`) referencing these integer IDs. This design is fundamentally incompatible with multi-device sync, graph merging, and content deduplication because:

1. Auto-increment integers are local to each database -- the same integer ID on two devices refers to different blocks.
2. GraphLoader already works around this by generating epoch-ms IDs and inserting them via `insertBlockWithId`, creating a fragile hybrid that is neither truly auto-increment nor truly globally unique.
3. UUIDs exist on both Block and Page models but are unused as FK references, making them decorative rather than structural.
4. The `left_id` FK is permanently set to NULL during graph loading because epoch-ms IDs cannot safely reference other blocks' integer IDs.

**User Value**: After this migration, users will be able to merge graphs from multiple devices without ID collisions, detect duplicate content across libraries, and use the database with replication solutions (CRSQLite, Electric SQL, Litestream) without conflict.

**Success Metrics**:
- All FK relationships (`parent_id`, `left_id`, `page_id`) use UUID TEXT columns.
- Zero reliance on auto-increment integers for entity identity.
- `left_id` sibling chains are correctly populated during graph loading (currently always NULL).
- Content deduplication query returns blocks with identical `content_hash` across merged graphs.
- Existing graphs migrate to the new schema without data loss.

**Scope**:
- **Included**: Schema migration, model changes, repository rewrites, GraphLoader updates, content hashing, CRDT metadata columns, migration tooling.
- **Excluded**: Implementing a full sync protocol, building a sync UI, CRSQLite integration (separate epic), conflict resolution UI.

---

## Architecture Decisions

### ADR-010: UUID as Primary Key for All Entities

- **Context**: Integer auto-increment PKs are local to a single database instance. When two devices create blocks independently, they generate overlapping integer IDs that point to different content. UUIDs (specifically UUIDv7) are globally unique and time-ordered, eliminating ID collisions during merge.
- **Decision**: Replace `INTEGER PRIMARY KEY AUTOINCREMENT` with `TEXT PRIMARY KEY` (UUID) on `blocks`, `pages`, and `properties` tables. All FK columns (`parent_id`, `left_id`, `page_id`, `block_id`) become TEXT referencing UUIDs.
- **Rationale**:
  - UUIDv7 preserves time-ordering (first 48 bits are epoch-ms), so index performance remains comparable to integer PKs for range scans.
  - The project already generates UUIDv7 via `UuidGenerator.generateV7()` and deterministic UUIDs via `generateDeterministic()` -- these are just not used as PKs.
  - SQLite TEXT keys with the UUID format (36 chars) add ~28 bytes per row over an 8-byte integer, but this is negligible for a note-taking app (even 100K blocks = ~2.8 MB overhead).
  - `block_references` already uses UUID columns (`from_block_uuid`, `to_block_uuid`), proving UUIDs work fine as identifiers in this codebase.
- **Consequences**:
  - FTS5 `content_rowid` must switch from integer `id` to a separate integer rowid alias, since FTS5 requires integer rowids. Solution: add `rowid` alias column or use SQLite's implicit rowid.
  - All SQL queries that use `WHERE id = ?` must be rewritten to `WHERE uuid = ?`.
  - The `Validation.validateId(id)` check on Long IDs becomes unnecessary; `Validation.validateUuid()` becomes the primary identity check.
  - JOIN performance on TEXT keys is slightly slower than INTEGER keys but well within acceptable bounds for this workload.

### ADR-011: Content-Addressable Hashing for Deduplication

- **Context**: When merging graphs from different devices, the same content may exist as different blocks with different UUIDs (e.g., user copied a page manually). We need a way to detect semantic duplicates.
- **Decision**: Add a `content_hash TEXT` column to `blocks` containing the SHA-256 hex digest of the normalized block content. Add a composite index on `(content_hash, page_id)` for fast duplicate detection within and across pages.
- **Rationale**:
  - SHA-256 is collision-resistant and available in Kotlin via `kotlin-crypto` or platform `MessageDigest`.
  - Normalizing content before hashing (trim whitespace, normalize line endings) reduces false negatives.
  - Storing the hash as a column (not computed on the fly) enables indexed duplicate queries.
- **Consequences**:
  - Hash must be recomputed on every content update. This is cheap (SHA-256 of a few KB is sub-millisecond).
  - The hash alone does not deduplicate -- it identifies candidates. A merge operation must decide which block to keep based on version/timestamp.

### ADR-012: CRDT-Ready Metadata Columns

- **Context**: For eventual consistency across devices, each mutation needs a causal ordering mechanism. Full CRDT implementation is out of scope, but the schema should be ready for it.
- **Decision**: Add `device_id TEXT`, `hlc_timestamp TEXT` (Hybrid Logical Clock), and `deleted INTEGER DEFAULT 0` (soft delete) columns to `blocks` and `pages`. These columns are nullable and unused in the initial migration but provide the foundation for CRDT-based sync.
- **Rationale**:
  - HLC timestamps (per Kulkarni et al., "Logical Physical Clocks") combine wall-clock time with a logical counter, enabling causal ordering without centralized coordination.
  - `device_id` identifies the origin of a mutation, enabling per-device conflict detection.
  - Soft deletes (`deleted` flag) prevent "resurrection" bugs where a deleted block reappears after sync because the delete event was not propagated.
  - Adding these columns now (even unused) avoids a second schema migration later.
- **Consequences**:
  - Adds 3 nullable columns per table (~50 bytes per row worst case). Negligible overhead.
  - All queries that list blocks must add `WHERE deleted = 0` filter. This is best handled at the repository layer.

---

## Current State Analysis

### Files Requiring Changes

| File | Current Role | Change Needed |
|------|-------------|---------------|
| `LogseqDatabase.sq` | Schema + all SQL queries | Full rewrite: UUID PKs, new columns, updated queries |
| `Models.kt` | `Block`, `Page` data classes with `id: Long` | Change `id` to `uuid: String` as primary identity; remove `id: Long` |
| `GraphLoader.kt` | Generates epoch-ms IDs, assigns UUIDs | Remove `generateId()`, use UUID as sole identity |
| `SqlDelightBlockRepository.kt` | All queries use `Long` id | Rewrite to use `String` UUID throughout |
| `SqlDelightPageRepository.kt` | All queries use `Long` id | Rewrite to use `String` UUID throughout |
| `GraphRepository.kt` | Interface with `getBlocksForPage(pageId: Long)` | Change signatures to UUID-based |
| `DriverFactory.jvm.kt` | Creates schema, sets PRAGMAs | Add migration support |
| `UuidGenerator.kt` | Generates UUIDv7 and deterministic UUIDs | Add content hashing utility |
| `Validation.kt` (in Models.kt) | `validateId(id: Long)` | Remove or deprecate; `validateUuid` is primary |

### Current FK Dependency Graph

```
pages.id (INTEGER AUTOINCREMENT)
  ^
  |-- blocks.page_id (INTEGER FK)
  
blocks.id (INTEGER AUTOINCREMENT)
  ^
  |-- blocks.parent_id (INTEGER FK, self-ref)
  |-- blocks.left_id (INTEGER FK, self-ref)
  |-- properties.block_id (INTEGER FK)
  
blocks.uuid (TEXT UNIQUE, NOT used as FK target)
  ^
  |-- block_references.from_block_uuid (TEXT FK)
  |-- block_references.to_block_uuid (TEXT FK)
```

### Target FK Dependency Graph

```
pages.uuid (TEXT PRIMARY KEY)
  ^
  |-- blocks.page_uuid (TEXT FK)
  
blocks.uuid (TEXT PRIMARY KEY)
  ^
  |-- blocks.parent_uuid (TEXT FK, self-ref)
  |-- blocks.left_uuid (TEXT FK, self-ref)
  |-- properties.block_uuid (TEXT FK)
  |-- block_references.from_block_uuid (TEXT FK)
  |-- block_references.to_block_uuid (TEXT FK)
```

---

## Story Breakdown

### Story 1: Schema Migration -- UUID Primary Keys

**User Value**: Establishes the foundational schema that supports cross-device identity.

**Acceptance Criteria**:
- `pages` table uses `uuid TEXT PRIMARY KEY` instead of `INTEGER PRIMARY KEY AUTOINCREMENT`.
- `blocks` table uses `uuid TEXT PRIMARY KEY` instead of `INTEGER PRIMARY KEY AUTOINCREMENT`.
- All FK columns reference UUID TEXT columns.
- FTS5 virtual table continues to function with the new schema.
- Existing data is migrated without loss via a versioned migration script.
- `PRAGMA foreign_keys=ON` passes with new schema (no FK violations).

#### Task 1.1: Write New Schema DDL

**File**: `kmp/src/commonMain/sqldelight/com/logseq/kmp/db/LogseqDatabase.sq`

Create the v2 schema:

```sql
CREATE TABLE pages (
    uuid TEXT PRIMARY KEY NOT NULL,
    name TEXT NOT NULL UNIQUE COLLATE NOCASE,
    namespace TEXT,
    file_path TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    properties TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    is_favorite INTEGER DEFAULT 0,
    is_journal INTEGER DEFAULT 0,
    journal_date TEXT,
    -- CRDT-ready columns (ADR-012)
    content_hash TEXT,
    device_id TEXT,
    hlc_timestamp TEXT,
    deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE blocks (
    uuid TEXT PRIMARY KEY NOT NULL,
    page_uuid TEXT NOT NULL,
    parent_uuid TEXT,
    left_uuid TEXT,
    content TEXT NOT NULL,
    level INTEGER NOT NULL DEFAULT 0,
    position INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    properties TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    content_hash TEXT,
    -- CRDT-ready columns (ADR-012)
    device_id TEXT,
    hlc_timestamp TEXT,
    deleted INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (page_uuid) REFERENCES pages(uuid) ON DELETE CASCADE,
    FOREIGN KEY (parent_uuid) REFERENCES blocks(uuid) ON DELETE CASCADE,
    FOREIGN KEY (left_uuid) REFERENCES blocks(uuid) ON DELETE SET NULL
);

CREATE TABLE properties (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    block_uuid TEXT NOT NULL,
    key TEXT NOT NULL,
    value TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    FOREIGN KEY (block_uuid) REFERENCES blocks(uuid) ON DELETE CASCADE
);
```

Note: `properties.id` retains AUTOINCREMENT because properties are local metadata without cross-device identity needs. `plugin_data.id` similarly stays integer-keyed.

**Estimated Complexity**: Medium -- mostly mechanical DDL rewrite, but FTS5 triggers need care.

#### Task 1.2: FTS5 Compatibility with TEXT Primary Key

FTS5 `content_rowid` requires an INTEGER. Since `blocks.uuid` is TEXT, the solution is:

```sql
-- SQLite always has an implicit integer rowid unless WITHOUT ROWID is specified.
-- Since we are NOT using WITHOUT ROWID, blocks will have an implicit rowid.
CREATE VIRTUAL TABLE blocks_fts USING fts5(
    content,
    content=blocks,
    content_rowid=rowid,
    tokenize='porter unicode61'
);

-- Triggers use rowid instead of uuid
CREATE TRIGGER blocks_ai AFTER INSERT ON blocks BEGIN
    INSERT INTO blocks_fts(rowid, content) VALUES (new.rowid, new.content);
END;

CREATE TRIGGER blocks_ad AFTER DELETE ON blocks BEGIN
    INSERT INTO blocks_fts(blocks_fts, rowid, content)
    VALUES('delete', old.rowid, old.content);
END;

CREATE TRIGGER blocks_au AFTER UPDATE ON blocks BEGIN
    INSERT INTO blocks_fts(blocks_fts, rowid, content)
    VALUES('delete', old.rowid, old.content);
    INSERT INTO blocks_fts(rowid, content) VALUES (new.rowid, new.content);
END;
```

**Estimated Complexity**: Low -- SQLite implicit rowid handles this naturally.

#### Task 1.3: Rewrite All SQL Queries

Every named query in `LogseqDatabase.sq` must be updated. Key patterns:

| Old Pattern | New Pattern |
|------------|-------------|
| `WHERE id = ?` | `WHERE uuid = ?` |
| `WHERE page_id = ?` | `WHERE page_uuid = ?` |
| `WHERE parent_id = ?` | `WHERE parent_uuid = ?` |
| `WHERE left_id = ?` | `WHERE left_uuid = ?` |
| `UPDATE ... SET parent_id = ? WHERE id = ?` | `UPDATE ... SET parent_uuid = ? WHERE uuid = ?` |
| `ORDER BY id` | `ORDER BY created_at` or `ORDER BY uuid` (UUIDv7 is time-ordered) |
| `LIMIT ? OFFSET ?` (on id) | Same, but ordered by `created_at` |
| `blocks.id = bm.rowid` (FTS join) | `blocks.rowid = bm.rowid` |

Add `WHERE deleted = 0` to all SELECT queries that return entities to users.

**Estimated Complexity**: High -- ~60 named queries to rewrite, each must be verified.

#### Task 1.4: Write Migration Script

SQLDelight supports versioned migrations via `.sqm` files. Create `1.sqm`:

```sql
-- Migration from v1 (integer PK) to v2 (UUID PK)

-- Step 1: Create new tables with UUID PKs
CREATE TABLE pages_v2 ( ... );
CREATE TABLE blocks_v2 ( ... );
CREATE TABLE properties_v2 ( ... );

-- Step 2: Copy data, mapping integer IDs to UUIDs
INSERT INTO pages_v2 (uuid, name, ...)
SELECT uuid, name, ... FROM pages;

INSERT INTO blocks_v2 (uuid, page_uuid, parent_uuid, left_uuid, ...)
SELECT 
    b.uuid,
    p.uuid AS page_uuid,
    pb.uuid AS parent_uuid,
    lb.uuid AS left_uuid,
    ...
FROM blocks b
JOIN pages p ON b.page_id = p.id
LEFT JOIN blocks pb ON b.parent_id = pb.id
LEFT JOIN blocks lb ON b.left_id = lb.id;

-- Step 3: Drop old tables, rename new
DROP TABLE blocks;
DROP TABLE pages;
DROP TABLE properties;
ALTER TABLE blocks_v2 RENAME TO blocks;
ALTER TABLE pages_v2 RENAME TO pages;
ALTER TABLE properties_v2 RENAME TO properties;

-- Step 4: Recreate indexes, FTS, triggers
```

**Estimated Complexity**: High -- must handle NULL parent_id/left_id, orphaned blocks, and FK ordering.

---

### Story 2: Model & Repository Layer Changes

**User Value**: Application code consistently uses UUIDs as identity, eliminating the dual-identity (Long id + String uuid) confusion.

**Acceptance Criteria**:
- `Block` data class has `uuid: String` as primary identity; `id: Long` is removed.
- `Page` data class has `uuid: String` as primary identity; `id: Long` is removed.
- `BlockRepository` interface methods use `String` (UUID) parameters everywhere.
- `PageRepository` interface methods use `String` (UUID) parameters everywhere.
- All call sites compile and pass tests.

#### Task 2.1: Update Domain Models

**File**: `kmp/src/commonMain/kotlin/com/logseq/kmp/model/Models.kt`

```kotlin
data class Block(
    val uuid: String,             // PRIMARY IDENTITY (was: id: Long + uuid: String)
    val pageUuid: String,         // was: pageId: Long
    val parentUuid: String? = null, // was: parentId: Long?
    val leftUuid: String? = null,   // was: leftId: Long?
    val content: String,
    val contentHash: String? = null, // NEW: SHA-256 of normalized content
    val level: Int = 0,
    val position: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long = 0,
    val properties: Map<String, String> = emptyMap(),
    val isLoaded: Boolean = true,
    val deviceId: String? = null,   // NEW: CRDT origin device
    val hlcTimestamp: String? = null, // NEW: Hybrid Logical Clock
    val deleted: Boolean = false     // NEW: Soft delete flag
)

data class Page(
    val uuid: String,             // PRIMARY IDENTITY (was: id: Long + uuid: String)
    val name: String,
    // ... rest unchanged except removing id: Long
)
```

Remove `Validation.validateId(id: Long)` calls from `init` blocks. Ensure `Validation.validateUuid()` is called on `uuid`, `pageUuid`, `parentUuid`, `leftUuid`.

**Estimated Complexity**: Medium -- many call sites reference `block.id` and `page.id`.

#### Task 2.2: Update Repository Interfaces

**File**: `kmp/src/commonMain/kotlin/com/logseq/kmp/repository/GraphRepository.kt`

Key signature changes:

```kotlin
interface BlockRepository {
    fun getBlocksForPage(pageUuid: String): Flow<Result<List<Block>>>  // was: pageId: Long
    suspend fun deleteBlocksForPage(pageUuid: String): Result<Unit>    // was: pageId: Long
    // ... all other methods already use UUID strings
}

interface PageRepository {
    fun getPageById(uuid: String): Flow<Result<Page?>>  // rename to getPageByUuid (or keep but change param type)
    // ... 
}
```

**Estimated Complexity**: Low -- interface changes are mechanical.

#### Task 2.3: Rewrite SqlDelightBlockRepository

**File**: `kmp/src/commonMain/kotlin/com/logseq/kmp/repository/SqlDelightBlockRepository.kt`

Major changes:
- All `Long` id parameters become `String` uuid.
- Cache keys change from `Long` to `String`.
- `toBlockModel()` mapper drops `this.id` and maps `this.uuid` as primary identity.
- `saveBlocks()` uses `insertBlock` (no more `insertBlockWithId` workaround).
- `splitBlock()` generates UUID via `UuidGenerator.generateV7()` instead of relying on auto-increment.

**Estimated Complexity**: High -- 700+ line file with deep ID dependencies.

#### Task 2.4: Rewrite SqlDelightPageRepository

**File**: `kmp/src/commonMain/kotlin/com/logseq/kmp/repository/SqlDelightPageRepository.kt`

Similar changes as Task 2.3. The `savePage()` method simplifies significantly because it no longer needs the "insert then lookup by name to get auto-assigned ID" workaround.

**Estimated Complexity**: Medium.

---

### Story 3: GraphLoader UUID-Native Loading

**User Value**: Graph loading correctly populates all FK relationships (including `left_uuid` sibling chains) and generates content hashes.

**Acceptance Criteria**:
- `generateId()` (epoch-ms counter) is removed entirely.
- All block identity comes from `generateUuid()` or `UuidGenerator.generateV7()`.
- `left_uuid` is correctly set during graph loading (currently always NULL).
- `content_hash` is computed and stored for every block.
- FK constraint violations are impossible during graph load (all references are UUIDs generated before insert).

#### Task 3.1: Remove Integer ID Generation

**File**: `kmp/src/commonMain/kotlin/com/logseq/kmp/db/GraphLoader.kt`

Delete:
- `idMutex` and `idCounter` fields
- `generateId()` suspend function
- All `val blockId = generateId()` / `val pageId = generateId()` calls

Replace with UUID-only identity:
- `val blockUuid = generateUuid(parsedBlock, pagePath, blockIndex)` -- already exists
- `val pageUuid = existingPage?.uuid ?: UuidGenerator.generateV7()` -- already exists

**Estimated Complexity**: Low -- mostly deletion of code.

#### Task 3.2: Populate `left_uuid` Sibling Chains

Currently, `left_id` is always NULL during graph loading (see comment on line 818-821 of GraphLoader.kt). With UUID-native design, this is fixable because UUIDs are known before insertion.

In `processParsedBlocks()`, track the previous sibling's UUID:

```kotlin
var previousSiblingUuid: String? = null
parsedBlocks.forEachIndexed { index, parsedBlock ->
    val blockUuid = generateUuid(parsedBlock, pagePath, index)
    val block = Block(
        uuid = blockUuid,
        leftUuid = previousSiblingUuid,  // NOW WORKS -- UUID is known pre-insert
        // ...
    )
    destinationList.add(block)
    previousSiblingUuid = blockUuid
    
    // Recurse into children (resets previousSiblingUuid for child scope)
    if (parsedBlock.children.isNotEmpty()) {
        processParsedBlocks(children, ..., parentUuid = blockUuid, ...)
    }
}
```

**Estimated Complexity**: Low -- the fix is straightforward once integer IDs are gone.

#### Task 3.3: Add Content Hashing

**File**: `kmp/src/commonMain/kotlin/com/logseq/kmp/util/ContentHasher.kt` (new utility)

```kotlin
object ContentHasher {
    fun hash(content: String): String {
        val normalized = content.trim().replace("\r\n", "\n").replace("\r", "\n")
        // Platform-specific SHA-256 via expect/actual
        return sha256Hex(normalized.encodeToByteArray())
    }
}
```

Requires `expect/actual` for SHA-256:
- JVM: `java.security.MessageDigest`
- Android: same
- JS: `crypto.subtle` (if re-enabled)
- iOS: `CommonCrypto` (if re-enabled)

Integrate into `GraphLoader.processParsedBlocks()`:
```kotlin
val block = Block(
    contentHash = ContentHasher.hash(parsedBlock.content),
    // ...
)
```

**Estimated Complexity**: Medium -- requires platform expect/actual for crypto.

---

### Story 4: Content Deduplication Queries

**User Value**: After merging graphs, users can identify and resolve duplicate content.

**Acceptance Criteria**:
- SQL query returns groups of blocks with identical `content_hash`.
- Repository exposes `findDuplicateBlocks(): Flow<Result<List<DuplicateGroup>>>`.
- Duplicates can be filtered by page, date range, or minimum group size.

#### Task 4.1: Add Deduplication SQL Queries

**File**: `LogseqDatabase.sq`

```sql
-- Find all duplicate content groups
selectDuplicateBlocks:
SELECT content_hash, COUNT(*) AS duplicate_count
FROM blocks
WHERE deleted = 0 AND content_hash IS NOT NULL
GROUP BY content_hash
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC
LIMIT ?;

-- Get all blocks in a duplicate group
selectBlocksByContentHash:
SELECT * FROM blocks
WHERE content_hash = ? AND deleted = 0
ORDER BY created_at;
```

#### Task 4.2: Add Deduplication to Repository Interface

```kotlin
data class DuplicateGroup(
    val contentHash: String,
    val blocks: List<Block>,
    val count: Int
)

interface BlockRepository {
    // ... existing methods ...
    fun findDuplicateBlocks(limit: Int = 50): Flow<Result<List<DuplicateGroup>>>
}
```

**Estimated Complexity**: Low.

---

### Story 5: CRDT-Ready Infrastructure

**User Value**: Schema is prepared for future multi-device sync without requiring another migration.

**Acceptance Criteria**:
- `device_id`, `hlc_timestamp`, `deleted` columns exist on `blocks` and `pages`.
- `DeviceIdProvider` generates a stable per-device identifier.
- `HybridLogicalClock` utility produces monotonically increasing timestamps.
- Soft-delete logic filters deleted entities from all read queries.

#### Task 5.1: Implement DeviceIdProvider

**File**: `kmp/src/commonMain/kotlin/com/logseq/kmp/sync/DeviceIdProvider.kt`

```kotlin
expect object DeviceIdProvider {
    fun getOrCreateDeviceId(): String
}
```

- JVM/Desktop: Store in `~/.config/logseq-kmp/device-id`
- Android: Store in `SharedPreferences`
- Generate as UUIDv4 on first launch, persist thereafter.

**Estimated Complexity**: Low.

#### Task 5.2: Implement HybridLogicalClock

**File**: `kmp/src/commonMain/kotlin/com/logseq/kmp/sync/HybridLogicalClock.kt`

```kotlin
data class HlcTimestamp(
    val wallClockMs: Long,
    val counter: Int,
    val deviceId: String
) {
    fun encode(): String = "$wallClockMs:$counter:$deviceId"
    
    companion object {
        fun decode(s: String): HlcTimestamp { /* parse */ }
    }
}

class HybridLogicalClock(private val deviceId: String) {
    private var lastTimestamp: HlcTimestamp = /* ... */
    
    @Synchronized
    fun now(): HlcTimestamp { /* Lamport-style increment */ }
    
    @Synchronized
    fun receive(remote: HlcTimestamp): HlcTimestamp { /* merge */ }
}
```

Reference: Kulkarni et al., "Logical Physical Clocks and Consistent Snapshots in Globally Distributed Databases" (2014).

**Estimated Complexity**: Medium -- algorithm is well-documented but needs correct implementation.

#### Task 5.3: Implement Soft Delete

All `SELECT` queries in the schema add `WHERE deleted = 0`. The repository layer handles this:

```kotlin
override suspend fun deleteBlock(blockUuid: String, deleteChildren: Boolean): Result<Unit> {
    // Soft delete: UPDATE blocks SET deleted = 1 WHERE uuid = ?
    // Hard delete available via separate purgeBlock() method
}
```

Add `purgeDeletedBlocks()` for garbage collection after sync confirms propagation.

**Estimated Complexity**: Medium -- touches all read queries.

---

### Story 6: Test Suite & Validation

**User Value**: Confidence that the migration is correct and the new schema handles edge cases.

**Acceptance Criteria**:
- Migration test: create v1 database, run migration, verify all data accessible via v2 queries.
- Round-trip test: save block via UUID, retrieve by UUID, verify all fields.
- FK integrity test: insert block with parent_uuid and left_uuid, verify CASCADE deletes work.
- Deduplication test: insert blocks with same content, verify `content_hash` groups them.
- Sibling chain test: load a page, verify `left_uuid` chain is correct for all blocks.
- Concurrent insert test: parallel block creation does not produce UUID collisions.

#### Task 6.1: Schema Migration Tests

Verify v1-to-v2 migration preserves:
- All pages with correct UUIDs
- All blocks with correct parent/left UUID references (not NULL where they should be populated)
- All block_references (already UUID-based, should be unaffected)
- FTS5 search functionality
- FK cascade behavior

#### Task 6.2: Repository Integration Tests

Rewrite existing tests in:
- `kmp/src/commonTest/kotlin/com/logseq/kmp/ui/screens/JournalsViewModelEditorTest.kt`
- `kmp/src/commonTest/kotlin/com/logseq/kmp/ui/screens/JournalsViewModelTest.kt`
- `kmp/src/jvmTest/kotlin/com/logseq/kmp/ui/fixtures/FakeRepositories.kt`

Update `FakeRepositories` to use UUID-based interfaces.

#### Task 6.3: GraphLoader Integration Tests

Test that `loadGraph()` produces:
- Correct `left_uuid` sibling chains
- Non-null `content_hash` on every block
- No FK violations (run with `PRAGMA foreign_keys=ON`)
- Deterministic UUIDs for blocks with `id::` properties

**Estimated Complexity**: Medium -- tests exist, need adaptation.

---

## Known Issues

### Bug: FTS5 Rowid Mismatch After Migration [SEVERITY: High]

**Description**: FTS5 `content_rowid` references implicit integer rowid. After migration, if rows are inserted in a different order than v1, rowids will differ, and any cached FTS index entries become stale.

**Mitigation**:
- Rebuild FTS index after migration: `INSERT INTO blocks_fts(blocks_fts) VALUES('rebuild');`
- Include this in the migration script (Task 1.4).

**Files Likely Affected**: `LogseqDatabase.sq` (migration script), `DriverFactory.jvm.kt`

### Bug: Orphaned Blocks During Migration [SEVERITY: High]

**Description**: If a block's `parent_id` references an integer ID that does not map to any existing block (orphaned FK), the UUID migration JOIN will produce NULL `parent_uuid`. With `FOREIGN KEY ... ON DELETE CASCADE`, inserting such a block would fail.

**Mitigation**:
- Pre-migration cleanup: identify and delete orphaned blocks before migration.
- Or: insert orphaned blocks with `parent_uuid = NULL` (making them root blocks).
- Log all orphaned blocks for user review.

**Files Likely Affected**: Migration script (Task 1.4), `GraphLoader.kt`

### Bug: Concurrent GraphLoader and Repository Access During Migration [SEVERITY: Medium]

**Description**: If the app starts loading a graph while migration is in progress, queries may fail against the partially migrated schema.

**Mitigation**:
- Run migration in a transaction (SQLite guarantees atomic transactions).
- Block all repository access until migration completes (use a `migrationComplete` flag or `Mutex`).

**Files Likely Affected**: `DriverFactory.jvm.kt`, `GraphLoader.kt`

### Bug: Content Hash Collision (Theoretical) [SEVERITY: Low]

**Description**: SHA-256 collision probability is negligible (1 in 2^128 for birthday attack), but if two genuinely different blocks produce the same hash, deduplication would incorrectly flag them.

**Mitigation**:
- Deduplication queries should always present both blocks to the user for confirmation.
- Never auto-delete based on hash match alone.

### Bug: UUIDv7 Clock Skew Across Devices [SEVERITY: Low]

**Description**: UUIDv7 embeds wall-clock time. If a device's clock is significantly wrong, UUIDs will sort incorrectly relative to other devices' UUIDs.

**Mitigation**:
- HLC timestamps (Story 5) handle this by using logical counters when wall clocks disagree.
- UUID ordering is convenience, not correctness -- the HLC is the source of truth for causal ordering.

---

## Implementation Phases

### Phase 1: Schema & Core (Stories 1-2)

**Goal**: New schema is live, models and repositories compile and pass tests.

**Dependencies**: None (greenfield migration).

**Rollback**: Keep v1 schema as backup; migration is reversible by restoring from backup DB file.

**Deliverables**:
- New `LogseqDatabase.sq` with UUID PKs
- Migration script `1.sqm`
- Updated `Block`, `Page` models
- Updated `BlockRepository`, `PageRepository` interfaces and implementations
- All existing tests passing against new schema

### Phase 2: GraphLoader & Content Hashing (Stories 3-4)

**Goal**: Graph loading is fully UUID-native with correct sibling chains and content hashes.

**Dependencies**: Phase 1 complete.

**Deliverables**:
- Simplified `GraphLoader` (no integer ID generation)
- `left_uuid` correctly populated
- `ContentHasher` expect/actual implementation
- Deduplication queries

### Phase 3: CRDT Foundation & Testing (Stories 5-6)

**Goal**: Schema is sync-ready, comprehensive test coverage validates the migration.

**Dependencies**: Phase 2 complete.

**Deliverables**:
- `DeviceIdProvider` expect/actual
- `HybridLogicalClock` implementation
- Soft delete logic
- Full test suite for migration, FK integrity, deduplication, sibling chains

---

## Replication Compatibility Notes

### CRSQLite (cr-sqlite)

CRSQLite requires tables to have a primary key and works best with application-generated IDs (not AUTOINCREMENT). UUID TEXT PKs are the recommended approach per the CRSQLite documentation. After this migration, enabling CRSQLite would require:

1. `SELECT crsql_as_crr('blocks');` and `SELECT crsql_as_crr('pages');`
2. CRSQLite will add its own metadata columns for CRDT tracking.
3. Our `device_id` and `hlc_timestamp` columns would complement CRSQLite's built-in tracking.

### Electric SQL

Electric SQL works with any PostgreSQL-compatible schema. UUID PKs are standard practice. The schema designed here is directly compatible.

### Litestream

Litestream replicates at the WAL level (not schema-aware). UUID PKs do not affect Litestream compatibility. The `PRAGMA journal_mode=WAL` already set in `DriverFactory.jvm.kt` is required.

---

## References

- Evans, E. (2003). "Domain-Driven Design". Addison-Wesley. -- Aggregate identity patterns.
- Kleppmann, M. (2017). "Designing Data-Intensive Applications". O'Reilly. -- Chapter 5: Replication, Chapter 12: CRDTs.
- Kulkarni, S. et al. (2014). "Logical Physical Clocks and Consistent Snapshots in Globally Distributed Databases". -- HLC algorithm.
- CRSQLite documentation: https://vlcn.io/docs/cr-sqlite/intro -- SQLite CRDT extension.
- RFC 9562 (2024). "Universally Unique IDentifiers (UUIDs)". -- UUIDv7 specification.
- Nygard, M. (2018). "Release It! Second Edition". Pragmatic Bookshelf. -- Migration safety patterns.
