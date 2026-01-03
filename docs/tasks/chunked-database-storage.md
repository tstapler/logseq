# Chunked Database Storage Feature Plan

**Document Version**: 1.0  
**Created**: 2026-01-03  
**Status**: Ready for Implementation  
**Estimated Duration**: 15 weeks (8 implementation + 7 rollout)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Problem Statement](#problem-statement)
3. [Solution Architecture](#solution-architecture)
4. [Architecture Decision Records](#architecture-decision-records)
5. [Implementation Epics](#implementation-epics)
6. [Dependency Map](#dependency-map)
7. [Known Issues and Bug Prevention](#known-issues-and-bug-prevention)
8. [Validation Strategy](#validation-strategy)
9. [Success Criteria](#success-criteria)
10. [Rollback Plan](#rollback-plan)

---

## Executive Summary

### Goal
Redesign Logseq's database persistence from a single Transit blob to a chunked, lazy-loadable format with optimized compression (zstd) and serialization (Transit MessagePack) to reduce startup time from 6+ seconds to <2 seconds while maintaining permanent backwards compatibility.

### Key Metrics

| Metric | Current | Target | Improvement |
|--------|---------|--------|-------------|
| Startup Time | 6.14s | <500ms critical | 92% faster |
| Time to Interactive | 6.14s | <500ms | 92% faster |
| Full Data Load | 6.14s | <2s | 68% faster |
| Storage Size | 113MB | ~20MB | 82% reduction |
| Incremental Save | 200ms (full) | <50ms | 75% faster |
| Memory at Startup | 100% | <30% | 70% reduction |

### Technology Stack
- **Serialization**: Transit MessagePack (15-30% smaller, 17-100% faster than JSON)
- **Compression**: zstd level 3 (7-8x faster compression, 2-3x faster decompression than gzip)
- **Storage**: Directory-based (Electron) / Chunked IndexedDB keys (Browser)

---

## Problem Statement

### Current State

The existing persistence layer in `/home/tstapler/Programming/logseq/src/main/frontend/db/persist.cljs` uses a monolithic approach:

```clojure
;; Current: Single Transit JSON blob
(defn get-serialized-graph [graph-name]
  (if (util/electron?)
    (ipc/ipc "getSerializedGraph" graph-name)  ;; Single 113MB file
    (idb/get-item graph-name)))                 ;; Single IndexedDB key
```

**Bottlenecks**:
1. **Monolithic Deserialization**: 6+ seconds to parse 113MB Transit JSON
2. **UI Thread Blocking**: Entire database must load before UI becomes interactive
3. **Memory Pressure**: 100% of data loaded into memory at startup
4. **Inefficient Saves**: Full database serialized on every change (200ms)
5. **No Lazy Loading**: Cannot load pages on-demand

### Affected Files

| File | Purpose | Lines |
|------|---------|-------|
| `/home/tstapler/Programming/logseq/src/main/frontend/db/persist.cljs` | Persistence layer | 57 |
| `/home/tstapler/Programming/logseq/src/main/frontend/db.cljs` | Database operations | 201 |
| `/home/tstapler/Programming/logseq/src/main/frontend/db/utils.cljs` | Serialization utilities | 112 |
| `/home/tstapler/Programming/logseq/src/electron/electron/handler.cljs` | Electron IPC handlers | 758 |
| `/home/tstapler/Programming/logseq/src/main/frontend/idb.cljs` | IndexedDB interface | 85 |

---

## Solution Architecture

### Chunking Strategy: Hybrid Page/Journal Model

**Chunk Types**:

| Type | Size Range | Purpose |
|------|------------|---------|
| Metadata | ~10KB | Version info, graph statistics, schema version |
| Config | ~100KB | Built-in pages, system configuration |
| Journal Monthly | 500KB-2MB | Aggregated journal entries by month |
| Page Chunks | 10KB-500KB | Individual pages with their blocks |

### Storage Layout

**Electron (Directory-Based)**:
```
~/.logseq/graphs/
  {graph-name}/
    manifest.msgpack.zst      # Index of all chunks
    metadata.msgpack.zst      # Version, schema, stats
    config.msgpack.zst        # Built-in pages
    journals/
      2025-12.msgpack.zst     # December 2025
      2025-11.msgpack.zst
    pages/
      programming.msgpack.zst
      ideas.msgpack.zst
  {graph-name}.transit        # OLD FORMAT (kept forever)
```

**Browser (IndexedDB Keys)**:
```
logseq_local_{path}/manifest
logseq_local_{path}/metadata
logseq_local_{path}/config
logseq_local_{path}/journal/2025-12
logseq_local_{path}/page/programming
```

### Three-Phase Progressive Loading

**Phase 1 - Critical (<500ms)**:
```
manifest (5KB) -> metadata -> config -> current month journal -> 5 recent pages
UI becomes INTERACTIVE here
```

**Phase 2 - Deferred (background, non-blocking)**:
```
Previous 2 journal months -> favorited pages -> frequently accessed pages
```

**Phase 3 - On-Demand**:
```
Historical journals (on scroll) -> pages (on link click) -> search results
```

### Manifest Format

```clojure
{:version 1
 :format-version "chunked-v1"
 :created-at #inst "2026-01-03T..."
 :last-updated #inst "2026-01-03T..."
 :serialization :msgpack
 :compression :zstd
 :compression-level 3
 :transit-caching true
 :chunks
 {:metadata {:key "metadata" :size 5432 :compressed-size 892 :hash "sha256..."}
  :config {:key "config" :size 98234 :compressed-size 12456 :hash "sha256..."}
  :journals
  {"2025-12" {:key "journals/2025-12" :size 1234567 :compressed-size 198234
              :date-range [20251201 20251231]}}
  :pages
  {"programming" {:key "pages/programming" :size 45678 :compressed-size 6789
                  :updated-at #inst "2026-01-02T..."}}}}
```

---

## Architecture Decision Records

### ADR-001: zstd Compression over gzip

**Status**: Accepted  
**Date**: 2026-01-03  
**Context**: Need high-performance compression for database chunks with browser compatibility.

**Decision**: Use zstd level 3 compression via `@oneidentity/zstd-js` (WebAssembly).

**Rationale**:

| Factor | gzip | zstd level 3 | Winner |
|--------|------|--------------|--------|
| Compression Speed | 100 MB/s | 530 MB/s | zstd (5.3x) |
| Decompression Speed | 440 MB/s | 1360 MB/s | zstd (3.1x) |
| Compression Ratio | 2.7-3x | 2.8-3.3x | zstd (11% better) |
| Browser Support | Native | WebAssembly | gzip (native) |
| CPU Impact | Higher | Lower | zstd |

**Consequences**:
- Requires WASM module loading (~50KB)
- Fallback to gzip if WASM fails to load
- Must handle async initialization

**Alternatives Considered**:
- **lzma**: Best ratio (3.5-4x) but 26x slower compression - rejected due to user-facing delays
- **brotli**: Good ratio but no streaming support in browsers - rejected
- **uncompressed**: Simplest but 82% larger storage - rejected

---

### ADR-002: Transit MessagePack over Transit JSON

**Status**: Accepted  
**Date**: 2026-01-03  
**Context**: Transit is already used; need to choose optimal wire format.

**Decision**: Use Transit MessagePack mode with write caching enabled.

**Rationale**:

| Factor | Transit JSON | Transit MessagePack | Winner |
|--------|--------------|---------------------|--------|
| Payload Size | Baseline | 15-30% smaller | MessagePack |
| Encoding Speed | Baseline | 17-100% faster | MessagePack |
| Decoding Speed | Baseline | 17-100% faster | MessagePack |
| Human Readable | Yes | No | JSON |
| Dictionary Encoding | Yes (caching) | Yes (caching) | Tie |

**Consequences**:
- Binary format not human-readable (debugging harder)
- Same Transit caching benefits apply
- No code changes to data structures required

**Implementation**:
```clojure
;; Writer with MessagePack + caching
(def msgpack-writer
  (transit/writer :msgpack {:cache-enabled? true}))

;; Reader
(def msgpack-reader
  (transit/reader :msgpack))
```

---

### ADR-003: Hybrid Page/Journal Chunking Strategy

**Status**: Accepted  
**Date**: 2026-01-03  
**Context**: Need to determine optimal chunk boundaries for lazy loading.

**Decision**: Use page-level chunks for regular pages, monthly aggregation for journals.

**Rationale**:
- Pages are natural atomic boundaries (independent units)
- Journals have predictable access patterns (recent first)
- Monthly aggregation balances chunk size (not too small = overhead)
- Aligns with existing DataScript schema (`:block/name`, `:block/page`)

**Chunk Size Analysis**:

| Chunk Type | Typical Size | Compressed | Count (4743 files) |
|------------|--------------|------------|-------------------|
| Metadata | 10KB | 1.5KB | 1 |
| Config | 100KB | 15KB | 1 |
| Journal/month | 1.5MB | 225KB | 24-36 |
| Page (small) | 20KB | 3KB | ~4000 |
| Page (large) | 500KB | 75KB | ~200 |

**Consequences**:
- Must handle pages >5MB specially (split by hierarchy)
- Journal monthly boundaries may split multi-day entries
- Cache invalidation more complex than monolithic

---

### ADR-004: Permanent Backwards Compatibility

**Status**: Accepted  
**Date**: 2026-01-03  
**Context**: User requirement for no deprecation of old format.

**Decision**: Maintain dual reader/writer pattern indefinitely.

**Rationale**:
- Users with old graphs must never lose access
- Rollback must always be possible
- Trust is essential for note-taking applications

**Implementation**:
```clojure
(defn detect-storage-format [graph-name]
  (p/let [manifest (get-chunk graph-name "manifest")]
    (if manifest
      {:format :chunked :version (:version manifest)}
      {:format :monolithic})))  ;; No version = always supported

(defn restore-graph! [repo]
  (p/let [format (detect-storage-format (datascript-db repo))]
    (case (:format format)
      :chunked (restore-chunked! repo)
      :monolithic (restore-monolithic! repo))))  ;; Permanent code path
```

**Consequences**:
- Two code paths to maintain forever
- Every release must test old format graphs
- Slightly larger codebase

---

## Implementation Epics

### Epic 1: Foundation Layer (Week 1-2)

**Goal**: Establish core chunking infrastructure with MessagePack serialization and zstd compression.

**New Files to Create**:
```
src/main/frontend/db/chunked.cljs          # Core chunking logic
src/main/frontend/db/chunked/manifest.cljs # Manifest operations
src/main/frontend/db/chunked/reader.cljs   # Chunk reading
src/main/frontend/db/chunked/writer.cljs   # Chunk writing
src/main/frontend/db/chunked/compress.cljs # Compression utilities
```

#### Story 1.1: Add npm Dependencies
**Context Boundary**: package.json, shadow-cljs.edn (2 files)  
**Estimated Duration**: 2 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 1.1.1 | Add `@oneidentity/zstd-js` to package.json | package.json | `npm install` succeeds |
| 1.1.2 | Add `msgpack-lite` to package.json | package.json | `npm install` succeeds |
| 1.1.3 | Configure shadow-cljs for WASM loading | shadow-cljs.edn | Build succeeds |
| 1.1.4 | Verify WASM loads in browser | test file | Console shows "zstd ready" |

**Acceptance Criteria**:
- [ ] npm install completes without errors
- [ ] zstd WASM module loads in browser console
- [ ] MessagePack encode/decode works in REPL

---

#### Story 1.2: Implement zstd Compression Wrapper
**Context Boundary**: compress.cljs (1 file)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 1.2.1 | Create compress.cljs namespace | chunked/compress.cljs | Namespace compiles |
| 1.2.2 | Implement async WASM initialization | chunked/compress.cljs | `(init!)` returns promise |
| 1.2.3 | Implement `compress` function (level 3) | chunked/compress.cljs | Roundtrip test passes |
| 1.2.4 | Implement `decompress` function | chunked/compress.cljs | Roundtrip test passes |
| 1.2.5 | Add gzip fallback when WASM unavailable | chunked/compress.cljs | Fallback triggers on error |
| 1.2.6 | Write unit tests for compression | test file | 10+ tests pass |

**Interface**:
```clojure
(ns frontend.db.chunked.compress
  (:require ["@oneidentity/zstd-js" :as zstd]))

(defn init! [] ...)                    ;; Returns promise when WASM ready
(defn compress [^bytes data] ...)      ;; Returns compressed bytes
(defn decompress [^bytes data] ...)    ;; Returns decompressed bytes
(defn compression-available? [] ...)   ;; True if zstd loaded
```

**Acceptance Criteria**:
- [ ] Compress/decompress roundtrip preserves data
- [ ] Compression ratio >70% on typical DataScript data
- [ ] Fallback to gzip works when WASM fails
- [ ] Performance: >100MB/s compression, >300MB/s decompression

---

#### Story 1.3: Implement Transit MessagePack Serialization
**Context Boundary**: reader.cljs, writer.cljs (2 files)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 1.3.1 | Create reader.cljs with MessagePack reader | chunked/reader.cljs | Namespace compiles |
| 1.3.2 | Create writer.cljs with MessagePack writer | chunked/writer.cljs | Namespace compiles |
| 1.3.3 | Enable Transit write caching | chunked/writer.cljs | Cache hit rate >50% |
| 1.3.4 | Add custom handlers for DataScript types | both files | Entity roundtrip works |
| 1.3.5 | Implement `serialize-chunk` function | chunked/writer.cljs | Returns bytes |
| 1.3.6 | Implement `deserialize-chunk` function | chunked/reader.cljs | Returns clj data |
| 1.3.7 | Write unit tests for serialization | test file | 15+ tests pass |

**Interface**:
```clojure
(ns frontend.db.chunked.writer
  (:require [cognitect.transit :as transit]))

(defn serialize-chunk [chunk-data]
  "Returns MessagePack bytes with Transit caching")

(ns frontend.db.chunked.reader)

(defn deserialize-chunk [bytes]
  "Returns Clojure data from MessagePack bytes")
```

**Acceptance Criteria**:
- [ ] All DataScript types serialize/deserialize correctly
- [ ] Transit caching reduces size by >15%
- [ ] Keywords, UUIDs, and dates handled correctly
- [ ] Performance: >50MB/s serialization

---

#### Story 1.4: Define Chunk Data Structures
**Context Boundary**: chunked.cljs (1 file)  
**Estimated Duration**: 3 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 1.4.1 | Create chunked.cljs core namespace | chunked.cljs | Namespace compiles |
| 1.4.2 | Define PageChunk record/spec | chunked.cljs | Spec validates |
| 1.4.3 | Define JournalChunk record/spec | chunked.cljs | Spec validates |
| 1.4.4 | Define MetadataChunk record/spec | chunked.cljs | Spec validates |
| 1.4.5 | Define ConfigChunk record/spec | chunked.cljs | Spec validates |
| 1.4.6 | Implement `extract-page-chunk` | chunked.cljs | Extracts page correctly |
| 1.4.7 | Implement `extract-journal-chunk` | chunked.cljs | Extracts month correctly |

**Data Structures**:
```clojure
(s/def ::page-chunk
  (s/keys :req-un [::page-name ::page-entity ::blocks]
          :opt-un [::properties]))

(s/def ::journal-chunk
  (s/keys :req-un [::year-month ::journals ::blocks]
          :opt-un [::date-range]))

(s/def ::metadata-chunk
  (s/keys :req-un [::schema-version ::block-count ::page-count ::created-at]))

(s/def ::config-chunk
  (s/keys :req-un [::built-in-pages ::settings]))
```

**Acceptance Criteria**:
- [ ] All chunk types have clojure.spec definitions
- [ ] Extract functions produce valid chunks
- [ ] Chunk extraction is deterministic

---

#### Story 1.5: Implement Manifest Operations
**Context Boundary**: manifest.cljs (1 file)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 1.5.1 | Create manifest.cljs namespace | chunked/manifest.cljs | Namespace compiles |
| 1.5.2 | Define Manifest spec | chunked/manifest.cljs | Spec validates |
| 1.5.3 | Implement `create-manifest` | chunked/manifest.cljs | Creates valid manifest |
| 1.5.4 | Implement `update-manifest` | chunked/manifest.cljs | Updates preserve data |
| 1.5.5 | Implement `read-manifest` | chunked/manifest.cljs | Parses stored manifest |
| 1.5.6 | Implement `write-manifest` | chunked/manifest.cljs | Writes atomically |
| 1.5.7 | Add checksum generation | chunked/manifest.cljs | SHA-256 hashes |
| 1.5.8 | Write unit tests | test file | 10+ tests pass |

**Acceptance Criteria**:
- [ ] Manifest roundtrip preserves all data
- [ ] Checksums verify chunk integrity
- [ ] Manifest updates are atomic

---

#### Story 1.6: Implement Format Detection
**Context Boundary**: persist.cljs (1 file, modification)  
**Estimated Duration**: 3 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 1.6.1 | Add `detect-storage-format` function | persist.cljs | Detects old format |
| 1.6.2 | Add manifest probe for chunked detection | persist.cljs | Detects new format |
| 1.6.3 | Handle version compatibility check | persist.cljs | Rejects incompatible |
| 1.6.4 | Write integration tests | test file | Both formats detected |

**Acceptance Criteria**:
- [ ] Old graphs detected as `:monolithic`
- [ ] New graphs detected as `:chunked`
- [ ] Incompatible versions show upgrade prompt

---

### Epic 2: Reading Infrastructure (Week 3-4)

**Goal**: Implement progressive chunk loading with LRU cache.

#### Story 2.1: Implement Electron Chunk IPC Handlers
**Context Boundary**: handler.cljs (1 file, modification)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 2.1.1 | Add `getChunk` IPC handler | handler.cljs | Returns chunk bytes |
| 2.1.2 | Add `saveChunk` IPC handler | handler.cljs | Saves with atomic write |
| 2.1.3 | Add `listChunks` IPC handler | handler.cljs | Lists all chunk keys |
| 2.1.4 | Add `deleteChunk` IPC handler | handler.cljs | Deletes safely |
| 2.1.5 | Ensure directory creation | handler.cljs | Creates parents |
| 2.1.6 | Write integration tests | test file | IPC roundtrip works |

**Implementation**:
```clojure
(defmethod handle :getChunk [_window [_ graph-name chunk-key]]
  (let [graph-dir (node-path/join (get-graphs-dir) graph-name)
        chunk-path (node-path/join graph-dir (str chunk-key ".msgpack.zst"))]
    (when (fs/existsSync chunk-path)
      (fs/readFileSync chunk-path))))

(defmethod handle :saveChunk [_window [_ graph-name chunk-key data]]
  (let [graph-dir (node-path/join (get-graphs-dir) graph-name)
        chunk-path (node-path/join graph-dir (str chunk-key ".msgpack.zst"))
        tmp-path (str chunk-path ".tmp")]
    (fs/mkdirSync (node-path/dirname chunk-path) #js {:recursive true})
    (fs/writeFileSync tmp-path data)
    (fs/renameSync tmp-path chunk-path)))
```

**Acceptance Criteria**:
- [ ] Chunk read/write works via IPC
- [ ] Atomic writes prevent corruption
- [ ] Directory structure created automatically

---

#### Story 2.2: Implement Browser Chunk IndexedDB Operations
**Context Boundary**: idb.cljs (1 file, modification)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 2.2.1 | Add `get-chunk` function | idb.cljs | Returns chunk data |
| 2.2.2 | Add `set-chunk` function | idb.cljs | Stores chunk data |
| 2.2.3 | Add `list-chunks` function | idb.cljs | Lists chunk keys |
| 2.2.4 | Add `delete-chunk` function | idb.cljs | Deletes chunk |
| 2.2.5 | Add quota monitoring | idb.cljs | Warns at 80% usage |
| 2.2.6 | Write unit tests | test file | Browser storage works |

**Acceptance Criteria**:
- [ ] Chunks stored in IndexedDB correctly
- [ ] Quota warnings displayed when approaching limit
- [ ] Works in Chrome, Firefox, Safari

---

#### Story 2.3: Implement LRU Cache for Chunks
**Context Boundary**: chunked.cljs (1 file)  
**Estimated Duration**: 3 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 2.3.1 | Implement LRU cache data structure | chunked.cljs | Eviction works |
| 2.3.2 | Add `cache-get` function | chunked.cljs | Returns cached chunk |
| 2.3.3 | Add `cache-put` function | chunked.cljs | Stores with eviction |
| 2.3.4 | Add cache statistics | chunked.cljs | Hit rate tracked |
| 2.3.5 | Configure cache size (default 20) | chunked.cljs | Configurable |
| 2.3.6 | Write unit tests | test file | LRU eviction correct |

**Acceptance Criteria**:
- [ ] LRU eviction works correctly
- [ ] Cache hit rate >60% for typical usage
- [ ] Memory bounded by cache size

---

#### Story 2.4: Implement Priority-Based Chunk Loader
**Context Boundary**: chunked/reader.cljs (1 file)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 2.4.1 | Define chunk priority levels | chunked/reader.cljs | Enum defined |
| 2.4.2 | Implement `load-chunk-async` | chunked/reader.cljs | Returns promise |
| 2.4.3 | Implement `load-critical-chunks` | chunked/reader.cljs | Phase 1 loads |
| 2.4.4 | Implement `load-deferred-chunks` | chunked/reader.cljs | Phase 2 background |
| 2.4.5 | Implement `load-chunk-on-demand` | chunked/reader.cljs | Phase 3 lazy |
| 2.4.6 | Add loading progress events | chunked/reader.cljs | UI can show progress |
| 2.4.7 | Write integration tests | test file | Progressive loading works |

**Priority Levels**:
```clojure
(def priority-levels
  {:critical 0      ;; manifest, metadata, config, current month
   :high 1          ;; recent pages, previous months
   :normal 2        ;; favorited pages
   :low 3           ;; historical data
   :on-demand 4})   ;; loaded only when accessed
```

**Acceptance Criteria**:
- [ ] Critical chunks load in <500ms
- [ ] UI interactive before deferred chunks load
- [ ] On-demand loading triggered by page access

---

#### Story 2.5: Implement restore-chunked-graph!
**Context Boundary**: db.cljs (1 file, modification)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 2.5.1 | Add `restore-chunked-graph!` function | db.cljs | Returns promise |
| 2.5.2 | Integrate with format detection | db.cljs | Correct path chosen |
| 2.5.3 | Implement progressive DataScript loading | db.cljs | Partial DB usable |
| 2.5.4 | Add stub entities for unloaded pages | db.cljs | Links work |
| 2.5.5 | Wire up to existing `restore-graph!` | db.cljs | Transparent switch |
| 2.5.6 | Write integration tests | test file | Full restore works |

**Implementation**:
```clojure
(defn restore-chunked-graph! [repo]
  (p/let [db-name (datascript-db repo)
          manifest (chunked/read-manifest db-name)
          ;; Phase 1: Critical
          metadata (chunked/load-chunk db-name "metadata")
          config (chunked/load-chunk db-name "config")
          current-journal (chunked/load-current-journal db-name)
          recent-pages (chunked/load-recent-pages db-name 5)
          ;; Build partial DB
          partial-db (chunked/build-partial-db metadata config current-journal recent-pages)
          _ (conn/reset-conn! (get-db repo false) partial-db)]
    ;; Schedule Phase 2 in background
    (js/setTimeout #(load-deferred-chunks! repo) 100)
    partial-db))
```

**Acceptance Criteria**:
- [ ] Partial database usable immediately
- [ ] Background loading non-blocking
- [ ] Page links work for unloaded pages

---

### Epic 3: Writing Infrastructure (Week 5-6)

**Goal**: Implement incremental chunk saves with atomic writes.

#### Story 3.1: Implement Chunk Diffing
**Context Boundary**: chunked/writer.cljs (1 file)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 3.1.1 | Implement `identify-changed-chunks` | chunked/writer.cljs | Detects changes |
| 3.1.2 | Track page changes from tx-data | chunked/writer.cljs | Page set correct |
| 3.1.3 | Track journal changes from tx-data | chunked/writer.cljs | Month set correct |
| 3.1.4 | Handle metadata changes | chunked/writer.cljs | Stats updated |
| 3.1.5 | Write unit tests | test file | Diff detection accurate |

**Implementation**:
```clojure
(defn identify-changed-chunks [tx-data]
  (let [affected-pages (into #{}
                             (map #(get-in % [:entity :block/page :block/name]))
                             tx-data)
        affected-journals (into #{}
                                (comp
                                  (filter #(get-in % [:entity :block/journal?]))
                                  (map #(journal-day->year-month
                                         (get-in % [:entity :block/journal-day]))))
                                tx-data)]
    {:pages affected-pages
     :journals affected-journals}))
```

**Acceptance Criteria**:
- [ ] Single page edit triggers only that page chunk
- [ ] Journal edit triggers correct month chunk
- [ ] Metadata updated when block count changes

---

#### Story 3.2: Implement Incremental Save Logic
**Context Boundary**: chunked/writer.cljs, db.cljs (2 files)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 3.2.1 | Implement `persist-chunked-incremental!` | chunked/writer.cljs | Saves changed chunks |
| 3.2.2 | Extract and serialize changed pages | chunked/writer.cljs | Chunks created |
| 3.2.3 | Extract and serialize changed journals | chunked/writer.cljs | Chunks created |
| 3.2.4 | Update manifest after saves | chunked/writer.cljs | Manifest current |
| 3.2.5 | Wire up to persist! function | db.cljs | Transparent switch |
| 3.2.6 | Write integration tests | test file | Incremental save works |

**Acceptance Criteria**:
- [ ] Only changed chunks saved (verify with logging)
- [ ] Manifest checksums updated
- [ ] Save time <50ms for single page edit

---

#### Story 3.3: Implement Atomic Chunk Writes
**Context Boundary**: handler.cljs, idb.cljs (2 files)  
**Estimated Duration**: 3 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 3.3.1 | Implement `.tmp` -> rename pattern (Electron) | handler.cljs | Atomic write |
| 3.3.2 | Implement transaction pattern (IndexedDB) | idb.cljs | Atomic write |
| 3.3.3 | Add write verification | both files | Checksum verified |
| 3.3.4 | Handle write failures gracefully | both files | Old chunk preserved |
| 3.3.5 | Write failure injection tests | test file | Recovery works |

**Acceptance Criteria**:
- [ ] Crash during write leaves old chunk intact
- [ ] Checksum verification catches corruption
- [ ] Write failures logged with context

---

#### Story 3.4: Implement Transaction Log for Chunk Invalidation
**Context Boundary**: chunked.cljs (1 file)  
**Estimated Duration**: 3 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 3.4.1 | Define transaction log structure | chunked.cljs | Spec defined |
| 3.4.2 | Implement `log-transaction` | chunked.cljs | Tx logged |
| 3.4.3 | Implement `get-pending-chunks` | chunked.cljs | Returns dirty chunks |
| 3.4.4 | Implement `clear-transaction-log` | chunked.cljs | Clears after flush |
| 3.4.5 | Handle recovery from tx log | chunked.cljs | Resume after crash |
| 3.4.6 | Write unit tests | test file | Tx log works |

**Acceptance Criteria**:
- [ ] Transaction log survives app restart
- [ ] Pending chunks identified on startup
- [ ] Recovery completes pending saves

---

### Epic 4: Migration and User Experience (Week 7)

**Goal**: Provide seamless migration with user controls.

#### Story 4.1: Implement Migration from Monolithic to Chunked
**Context Boundary**: chunked/migrate.cljs (1 new file)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 4.1.1 | Create migrate.cljs namespace | chunked/migrate.cljs | Namespace compiles |
| 4.1.2 | Implement `migrate-to-chunked!` | chunked/migrate.cljs | Full migration |
| 4.1.3 | Extract all pages to chunks | chunked/migrate.cljs | Page chunks created |
| 4.1.4 | Extract all journals to monthly chunks | chunked/migrate.cljs | Journal chunks created |
| 4.1.5 | Create metadata and config chunks | chunked/migrate.cljs | System chunks created |
| 4.1.6 | Generate manifest with checksums | chunked/migrate.cljs | Manifest created |
| 4.1.7 | Verify migration integrity | chunked/migrate.cljs | Block count matches |
| 4.1.8 | Preserve old format as backup | chunked/migrate.cljs | .transit kept |
| 4.1.9 | Write integration tests | test file | Migration roundtrip |

**Acceptance Criteria**:
- [ ] All data migrated correctly (block count matches)
- [ ] Old format preserved for rollback
- [ ] Migration time <30s for 5000 pages

---

#### Story 4.2: Build Migration Progress UI
**Context Boundary**: ui/migrate.cljs (1 new file)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 4.2.1 | Create migration progress component | ui/migrate.cljs | Component renders |
| 4.2.2 | Show phase progress (pages, journals) | ui/migrate.cljs | Progress visible |
| 4.2.3 | Display estimated time remaining | ui/migrate.cljs | ETA shown |
| 4.2.4 | Handle migration errors gracefully | ui/migrate.cljs | Error displayed |
| 4.2.5 | Add cancel migration option | ui/migrate.cljs | Cancel works |
| 4.2.6 | Show completion summary | ui/migrate.cljs | Stats displayed |

**Acceptance Criteria**:
- [ ] Progress bar updates during migration
- [ ] Error messages actionable
- [ ] Cancel preserves old format

---

#### Story 4.3: Implement Data Integrity Verification
**Context Boundary**: chunked/migrate.cljs (1 file)  
**Estimated Duration**: 3 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 4.3.1 | Implement `verify-migration!` | chunked/migrate.cljs | Returns bool |
| 4.3.2 | Compare block counts | chunked/migrate.cljs | Counts match |
| 4.3.3 | Verify all page names present | chunked/migrate.cljs | Names match |
| 4.3.4 | Verify chunk checksums | chunked/migrate.cljs | Hashes valid |
| 4.3.5 | Generate verification report | chunked/migrate.cljs | Report logged |
| 4.3.6 | Write verification tests | test file | Verification accurate |

**Acceptance Criteria**:
- [ ] Verification catches data loss
- [ ] Checksum failures trigger repair prompt
- [ ] Report includes actionable details

---

#### Story 4.4: Add Settings UI for Format Selection
**Context Boundary**: settings.cljs (1 file, modification)  
**Estimated Duration**: 3 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 4.4.1 | Add "Storage Format" setting section | settings.cljs | Section visible |
| 4.4.2 | Add format toggle (Chunked/Monolithic) | settings.cljs | Toggle works |
| 4.4.3 | Show current graph format | settings.cljs | Format displayed |
| 4.4.4 | Add "Migrate Now" button | settings.cljs | Button triggers migration |
| 4.4.5 | Add "Rollback to Monolithic" button | settings.cljs | Rollback works |
| 4.4.6 | Persist preference to config | settings.cljs | Preference saved |

**Acceptance Criteria**:
- [ ] Users can opt-out of chunked storage
- [ ] Rollback available and works
- [ ] Settings persist across sessions

---

### Epic 5: Optimization and Testing (Week 8)

**Goal**: Validate performance targets and ensure reliability.

#### Story 5.1: Performance Benchmarking
**Context Boundary**: test files (3 files)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 5.1.1 | Create performance test harness | test file | Harness runs |
| 5.1.2 | Benchmark startup time (small graph) | test file | <200ms |
| 5.1.3 | Benchmark startup time (medium graph) | test file | <500ms |
| 5.1.4 | Benchmark startup time (large graph) | test file | <2000ms |
| 5.1.5 | Benchmark incremental save time | test file | <50ms |
| 5.1.6 | Compare old vs new performance | test file | Report generated |

**Acceptance Criteria**:
- [ ] All performance targets met
- [ ] Benchmarks reproducible
- [ ] Results logged to CI

---

#### Story 5.2: Memory Profiling
**Context Boundary**: test files (2 files)  
**Estimated Duration**: 3 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 5.2.1 | Create memory profiling test | test file | Profile runs |
| 5.2.2 | Measure heap size at startup | test file | <30% of full |
| 5.2.3 | Verify chunks garbage collected | test file | No leaks |
| 5.2.4 | Verify LRU cache bounded | test file | Memory stable |
| 5.2.5 | Generate memory report | test file | Report logged |

**Acceptance Criteria**:
- [ ] Memory usage reduced by 50%+
- [ ] No memory leaks detected
- [ ] LRU cache eviction works

---

#### Story 5.3: Edge Case Testing
**Context Boundary**: test files (3 files)  
**Estimated Duration**: 4 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 5.3.1 | Test missing chunk handling | test file | Graceful degradation |
| 5.3.2 | Test corrupted chunk handling | test file | Error shown, continues |
| 5.3.3 | Test chunk >5MB handling | test file | Split correctly |
| 5.3.4 | Test concurrent window access | test file | No corruption |
| 5.3.5 | Test interrupted save recovery | test file | Recovery works |
| 5.3.6 | Test quota exceeded handling | test file | Warning shown |

**Acceptance Criteria**:
- [ ] All edge cases handled gracefully
- [ ] No data loss in any scenario
- [ ] User-friendly error messages

---

#### Story 5.4: Backwards Compatibility Testing
**Context Boundary**: test files (2 files)  
**Estimated Duration**: 3 hours

**Atomic Tasks**:

| Task ID | Description | Files | Validation |
|---------|-------------|-------|------------|
| 5.4.1 | Create test graphs from v0.10.15 | test file | Graphs created |
| 5.4.2 | Test loading old format graphs | test file | Load succeeds |
| 5.4.3 | Test migration from old format | test file | Migration succeeds |
| 5.4.4 | Test rollback to old format | test file | Rollback succeeds |
| 5.4.5 | Add to CI regression suite | CI config | CI runs tests |

**Acceptance Criteria**:
- [ ] Old graphs load without prompt
- [ ] Migration preserves all data
- [ ] Rollback works seamlessly

---

## Dependency Map

```
Epic 1: Foundation
  |
  +-- Story 1.1 (npm deps) -----> Story 1.2 (compression)
  |                                    |
  +-- Story 1.3 (serialization) <------+
  |         |
  +-- Story 1.4 (data structures) <----+
  |         |
  +-- Story 1.5 (manifest) <-----------+
  |         |
  +-- Story 1.6 (format detection) <---+
            |
            v
Epic 2: Reading Infrastructure
  |
  +-- Story 2.1 (Electron IPC) -------> Story 2.4 (priority loader)
  |                                          |
  +-- Story 2.2 (IndexedDB) --------------->|
  |                                          |
  +-- Story 2.3 (LRU cache) --------------->|
  |                                          |
  +-- Story 2.5 (restore-chunked!) <--------+
            |
            v
Epic 3: Writing Infrastructure
  |
  +-- Story 3.1 (chunk diffing) -----> Story 3.2 (incremental save)
  |                                          |
  +-- Story 3.3 (atomic writes) <-----------+
  |                                          |
  +-- Story 3.4 (tx log) <------------------+
            |
            v
Epic 4: Migration & UX
  |
  +-- Story 4.1 (migration) ---------> Story 4.2 (progress UI)
  |                                          |
  +-- Story 4.3 (verification) <------------+
  |                                          |
  +-- Story 4.4 (settings UI) <-------------+
            |
            v
Epic 5: Optimization & Testing
  |
  +-- Story 5.1 (benchmarks)
  +-- Story 5.2 (memory profiling)
  +-- Story 5.3 (edge cases)
  +-- Story 5.4 (backwards compat)
```

### Integration Checkpoints

| Checkpoint | After Story | Validation |
|------------|-------------|------------|
| CP1 | 1.6 | Compression + serialization roundtrip works |
| CP2 | 2.5 | Chunked graph loads in Electron |
| CP3 | 3.2 | Incremental saves work end-to-end |
| CP4 | 4.3 | Migration preserves all data |
| CP5 | 5.4 | All performance targets met |

---

## Known Issues and Bug Prevention

### ISSUE-001: Race Condition in Concurrent Chunk Writes

**Severity**: High  
**Phase**: Epic 3 (Writing)

**Description**: Multiple windows editing the same graph may write to the same chunk simultaneously, causing data loss.

**Affected Files**:
- `/home/tstapler/Programming/logseq/src/main/frontend/db/chunked/writer.cljs`
- `/home/tstapler/Programming/logseq/src/electron/electron/handler.cljs`

**Mitigation**:
1. Use existing `dbsync` mechanism for cross-window coordination
2. Implement chunk-level locking with timeouts
3. Add conflict detection in manifest (version vectors)
4. Queue writes through single coordinator per graph

**Prevention Strategy**:
```clojure
;; Use lock file pattern
(defn acquire-chunk-lock [chunk-key timeout-ms]
  (let [lock-path (str chunk-key ".lock")]
    (loop [attempts 0]
      (cond
        (> attempts (/ timeout-ms 100))
        (throw (ex-info "Chunk lock timeout" {:chunk chunk-key}))
        
        (try-create-lock-file lock-path)
        {:lock-acquired true :lock-path lock-path}
        
        :else
        (do (Thread/sleep 100) (recur (inc attempts)))))))
```

**Test Cases**:
- Concurrent edit from two windows
- Edit during save in progress
- Lock timeout handling

---

### ISSUE-002: Orphaned Chunks After Interrupted Migration

**Severity**: Medium  
**Phase**: Epic 4 (Migration)

**Description**: If migration crashes after creating some chunks but before updating manifest, chunks may be orphaned.

**Affected Files**:
- `/home/tstapler/Programming/logseq/src/main/frontend/db/chunked/migrate.cljs`

**Mitigation**:
1. Write manifest last, atomically
2. Use transaction log to track migration state
3. Implement orphan chunk cleanup on startup
4. Keep old format until verification passes

**Prevention Strategy**:
```clojure
(defn migrate-with-recovery! [graph-name]
  (let [tx-log (get-or-create-tx-log graph-name)]
    (try
      (when-not (:migration-complete? tx-log)
        ;; Resume from last checkpoint
        (let [completed-chunks (:completed-chunks tx-log [])]
          (doseq [chunk (remaining-chunks completed-chunks)]
            (migrate-chunk! chunk)
            (log-chunk-complete! tx-log chunk))))
      ;; Write manifest only after all chunks
      (write-manifest-atomic! graph-name)
      (mark-migration-complete! tx-log)
      (catch Exception e
        (log-migration-error! tx-log e)
        (throw e)))))
```

**Test Cases**:
- Kill process during migration
- Disk full during chunk write
- Resume interrupted migration

---

### ISSUE-003: Memory Leak from Unreleased Chunk References

**Severity**: Medium  
**Phase**: Epic 2 (Reading)

**Description**: Chunks loaded into memory may not be garbage collected if references are retained in closures or global state.

**Affected Files**:
- `/home/tstapler/Programming/logseq/src/main/frontend/db/chunked.cljs`

**Mitigation**:
1. Use weak references for non-active chunks
2. Ensure LRU cache eviction clears all references
3. Profile memory in browser DevTools
4. Add memory guards that force GC when heap exceeds threshold

**Prevention Strategy**:
```clojure
(defn load-chunk-with-cleanup [chunk-key]
  (let [chunk (load-chunk chunk-key)]
    ;; Register cleanup callback
    (register-cleanup-on-evict chunk-key
      (fn []
        ;; Clear any retained references
        (clear-chunk-subscriptions chunk-key)
        (remove-from-query-cache chunk-key)))
    chunk))
```

**Test Cases**:
- Load 100 pages, verify heap stable
- Navigate away and back, verify no leak
- Force GC and verify chunk count

---

### ISSUE-004: zstd WASM Initialization Failure in Older Browsers

**Severity**: Medium  
**Phase**: Epic 1 (Foundation)

**Description**: WebAssembly may fail to load in older browsers or restrictive environments (CSP).

**Affected Files**:
- `/home/tstapler/Programming/logseq/src/main/frontend/db/chunked/compress.cljs`

**Mitigation**:
1. Detect WASM support before loading
2. Fallback to gzip compression
3. Show warning suggesting browser upgrade
4. Store fallback format in manifest

**Prevention Strategy**:
```clojure
(defn init-compression! []
  (if (wasm-supported?)
    (-> (load-zstd-wasm!)
        (p/then (fn [_] (reset! compression-backend :zstd)))
        (p/catch (fn [e]
                   (log/warn "zstd WASM failed, using gzip" e)
                   (reset! compression-backend :gzip))))
    (do
      (log/warn "WASM not supported, using gzip")
      (reset! compression-backend :gzip))))
```

**Test Cases**:
- Test in browser with WASM disabled
- Test with Content-Security-Policy blocking WASM
- Verify gzip fallback produces valid chunks

---

### ISSUE-005: Circular Reference in Page Links Causing Infinite Loop

**Severity**: High  
**Phase**: Epic 2 (Reading)

**Description**: Pages referencing each other (A -> B -> A) may cause infinite loops during lazy loading.

**Affected Files**:
- `/home/tstapler/Programming/logseq/src/main/frontend/db/chunked/reader.cljs`

**Mitigation**:
1. Track loading set to detect cycles
2. Use stub entities for unloaded referenced pages
3. Limit reference depth during initial load
4. Load full graph eventually in background

**Prevention Strategy**:
```clojure
(defn load-page-with-refs [page-name loading-set]
  (if (contains? loading-set page-name)
    ;; Return stub to break cycle
    (create-stub-entity page-name)
    (let [chunk (load-chunk (str "pages/" page-name))
          refs (extract-page-refs chunk)]
      ;; Load refs but don't recurse infinitely
      (doseq [ref refs]
        (when-not (loaded? ref)
          (load-page-with-refs ref (conj loading-set page-name))))
      chunk)))
```

**Test Cases**:
- Create A -> B -> A reference
- Create A -> B -> C -> A reference
- Verify all pages eventually loaded

---

### ISSUE-006: Data Loss from Unsaved Transaction During Crash

**Severity**: Critical  
**Phase**: Epic 3 (Writing)

**Description**: If application crashes after DataScript transaction but before chunk save, data is lost.

**Affected Files**:
- `/home/tstapler/Programming/logseq/src/main/frontend/db.cljs`
- `/home/tstapler/Programming/logseq/src/main/frontend/db/chunked/writer.cljs`

**Mitigation**:
1. Write transaction log before modifying in-memory DB
2. Replay transaction log on startup
3. Periodic checkpoint saves (every 30 seconds)
4. Beforeunload handler for controlled shutdown

**Prevention Strategy**:
```clojure
(defn transact-with-durability! [repo tx-data]
  ;; Write to transaction log FIRST
  (p/let [tx-id (write-tx-log! repo tx-data)]
    ;; Then apply to in-memory DB
    (d/transact! (get-db repo false) tx-data)
    ;; Mark as persisted after chunk save completes
    (p/let [_ (persist-chunked-incremental! repo)]
      (mark-tx-persisted! repo tx-id))))

;; On startup
(defn recover-pending-transactions! [repo]
  (let [pending-txs (read-pending-tx-log repo)]
    (doseq [tx pending-txs]
      (d/transact! (get-db repo false) (:data tx)))))
```

**Test Cases**:
- Kill process after transaction
- Verify data recovered on restart
- Stress test with rapid transactions

---

### ISSUE-007: Manifest Corruption Causing Graph Inaccessibility

**Severity**: Critical  
**Phase**: Epic 1 (Foundation)

**Description**: If manifest is corrupted, entire graph becomes inaccessible even though chunk data is intact.

**Affected Files**:
- `/home/tstapler/Programming/logseq/src/main/frontend/db/chunked/manifest.cljs`

**Mitigation**:
1. Keep previous manifest version as backup
2. Implement manifest reconstruction from chunks
3. Store chunk index redundantly in each chunk header
4. Checksum manifest and verify on read

**Prevention Strategy**:
```clojure
(defn write-manifest-safe! [graph-name manifest]
  (let [current-path (manifest-path graph-name)
        backup-path (str current-path ".bak")
        tmp-path (str current-path ".tmp")]
    ;; Backup current
    (when (exists? current-path)
      (copy! current-path backup-path))
    ;; Write new atomically
    (write! tmp-path (serialize manifest))
    (rename! tmp-path current-path)))

(defn read-manifest-with-recovery [graph-name]
  (or (try (read-manifest graph-name)
           (catch :default _ nil))
      (try (read-manifest-backup graph-name)
           (catch :default _ nil))
      (reconstruct-manifest-from-chunks graph-name)))
```

**Test Cases**:
- Corrupt manifest file
- Verify recovery from backup
- Verify reconstruction from chunks

---

## Validation Strategy

### Unit Tests (Target: 200+ tests)

| Category | Count | Coverage Target |
|----------|-------|-----------------|
| Compression | 20 | 95% |
| Serialization | 30 | 95% |
| Chunk Operations | 40 | 90% |
| Manifest Operations | 20 | 95% |
| LRU Cache | 15 | 95% |
| Format Detection | 10 | 100% |
| Migration | 25 | 90% |
| Error Handling | 40 | 85% |

### Integration Tests

| Scenario | Description | Duration Target |
|----------|-------------|-----------------|
| Full Save/Load Cycle | Save 1000 pages, reload, verify | <30s |
| Incremental Save | Edit page, verify only that chunk updated | <1s |
| Progressive Loading | Verify UI interactive in <500ms | <2s |
| Migration Roundtrip | Migrate, verify, rollback, verify | <60s |
| Concurrent Windows | Two windows editing same graph | <10s |

### Performance Tests

| Test | Graph Size | Target |
|------|------------|--------|
| Startup (small) | 100 pages | <200ms |
| Startup (medium) | 1000 pages | <500ms |
| Startup (large) | 5000 pages | <2000ms |
| Incremental Save | Any | <50ms |
| Compression Ratio | 113MB input | >80% reduction |

### Browser Compatibility

| Browser | Minimum Version | Test Status |
|---------|-----------------|-------------|
| Chrome | 80+ | Required |
| Firefox | 78+ | Required |
| Safari | 14+ | Required |
| Edge | 88+ | Required |

---

## Success Criteria

### Performance (Must Meet All)

- [ ] UI interactive in <500ms (92% improvement from 6.14s)
- [ ] Full data load in <2s (68% improvement)
- [ ] Incremental save in <50ms
- [ ] Storage reduced by 80%+ (113MB to ~20MB)

### Reliability (Must Meet All)

- [ ] Zero data loss in migration (verified by block count)
- [ ] Checksum verification catches 100% of corruption
- [ ] Graceful degradation when chunks missing
- [ ] Recovery from interrupted saves

### Compatibility (Must Meet All)

- [ ] Old graphs load without migration prompt
- [ ] User can opt-out of chunked storage
- [ ] Rollback to monolithic works
- [ ] Works in all supported browsers

### Quality (Must Meet All)

- [ ] 80%+ test coverage
- [ ] Zero critical bugs in beta
- [ ] <1% error rate in production
- [ ] All ADRs documented

---

## Rollback Plan

### Automatic Rollback Triggers

1. Error rate >1% in first 24 hours
2. Data loss reported by any user
3. Performance regression >20%
4. Memory usage increase >50%

### Rollback Procedure

1. **Feature Flag**: Set `chunked-storage-enabled` to `false`
2. **User Communication**: Display "Reverting to previous storage format"
3. **Data Preservation**: Old `.transit` files are never deleted
4. **Verification**: Confirm old format loads correctly

### Rollback Timeline

| Phase | Duration | Action |
|-------|----------|--------|
| Detection | <1 hour | Monitoring alerts on anomalies |
| Decision | <2 hours | Team reviews metrics |
| Rollback | <30 minutes | Feature flag toggle |
| Verification | <1 hour | Confirm users can access data |

---

## Appendix: File Reference

### New Files to Create

| Path | Purpose | Lines (Est.) |
|------|---------|--------------|
| `src/main/frontend/db/chunked.cljs` | Core chunking logic | 200 |
| `src/main/frontend/db/chunked/manifest.cljs` | Manifest operations | 150 |
| `src/main/frontend/db/chunked/reader.cljs` | Chunk reading | 200 |
| `src/main/frontend/db/chunked/writer.cljs` | Chunk writing | 200 |
| `src/main/frontend/db/chunked/compress.cljs` | Compression utilities | 100 |
| `src/main/frontend/db/chunked/migrate.cljs` | Migration tools | 250 |
| `src/main/frontend/ui/migrate.cljs` | Migration UI | 150 |

### Files to Modify

| Path | Changes | Impact |
|------|---------|--------|
| `src/main/frontend/db/persist.cljs` | Add chunk operations, format detection | Medium |
| `src/main/frontend/db.cljs` | Add restore-chunked-graph!, modify persist! | Medium |
| `src/main/frontend/db/utils.cljs` | Add chunk extraction helpers | Low |
| `src/electron/electron/handler.cljs` | Add chunk IPC handlers | Medium |
| `src/main/frontend/idb.cljs` | Add chunk-level IndexedDB operations | Medium |
| `package.json` | Add npm dependencies | Low |
| `shadow-cljs.edn` | Configure WASM loading | Low |

---

**Document Status**: Ready for Review  
**Next Steps**: 
1. Review with team
2. Create GitHub issues for each story
3. Begin Phase 1 implementation
