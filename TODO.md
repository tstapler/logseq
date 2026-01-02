# TODO.md - Logseq Kotlin Multiplatform Migration

## Current Status
- **Migration State**: 65% complete - All repository backends implemented
- **Technology Stack**: Kotlin Multiplatform with SQLDelight 2.0
- **Recent Activity**: Comprehensive bug analysis and strategic planning completed
- **Last Updated**: January 1, 2026

## Active Backends

### ✅ IN_MEMORY Backend
- `InMemoryBlockRepository` - Working with full CRUD
- `InMemoryPageRepository` - Working with full CRUD
- `InMemoryPropertyRepository` - Inline in RepositoryFactoryImpl (needs extraction)
- `InMemoryReferenceRepository` - Full implementation
- Performance: ~2,000-20,000 ops/sec depending on operation

### ✅ DATASCRIPT Backend
- `DatascriptBlockRepository` - Datalog-style indexing, optimized queries
- `DatascriptPageRepository` - Datalog-style indexing, optimized queries
- `DatascriptPropertyRepository` - Full implementation
- `DatascriptReferenceRepository` - Full implementation
- Performance: ~3,000-30,000 ops/sec (15-50% faster than IN_MEMORY)

### ✅ SQLDelight Backend
- `SqlDelightBlockRepository` - Full CRUD with hierarchical queries using SQLDelight
- `SqlDelightPageRepository` - Full CRUD with namespace support using SQLDelight
- `SqlDelightPropertyRepository` - Full implementation with queries
- `SqlDelightReferenceRepository` - Full implementation with queries
- Uses `JdbcSqliteDriver` for SQLite database access
- Performance: Benchmarked, awaiting optimization

## Benchmark Results (IN_MEMORY vs DATASCRIPT)

| Operation | IN_MEMORY | DATASCRIPT | Winner |
|-----------|-----------|------------|--------|
| Block Create | 243.6µs | 204.3µs | DATASCRIPT 1.2x |
| Block Retrieve | 62.2µs | 48.0µs | DATASCRIPT 1.3x |
| Block Get Hierarchy | 76.1µs | 39.9µs | DATASCRIPT 1.9x |
| Page Create | 96.9µs | 148.8µs | IN_MEMORY 1.5x |
| Page Get All | 49.3µs | 32.4µs | DATASCRIPT 1.5x |
| Page Get Recent | 51.7µs | 41.9µs | DATASCRIPT 1.2x |

## Completed Tasks

### Repository Layer ✅
- [x] Define core repository interfaces (BlockRepository, PageRepository, PropertyRepository)
- [x] Implement InMemoryBlockRepository with hierarchical operations
- [x] Implement InMemoryPageRepository with namespace support
- [x] Implement DatascriptBlockRepository with Datalog indexing
- [x] Implement DatascriptPageRepository with Datalog indexing
- [x] Implement SqlDelightBlockRepository with SQL queries
- [x] Implement SqlDelightPageRepository with SQL queries
- [x] Create RepositoryFactoryImpl for backend switching
- [x] Update AllBackendsComparisonBenchmark with SQLDelight
- [x] Document architecture: docs/ARCHITECTURE_DATASCRIPT_SQLITE.md

### Reference Repository Layer ✅
- [x] Implement InMemoryReferenceRepository with reference tracking
- [x] Implement DatascriptReferenceRepository with Datalog-style indexing
- [x] Implement SqlDelightReferenceRepository with SQL queries
- [x] Add reference queries to LogseqDatabase schema

### Property Repository Layer ✅
- [x] Implement DatascriptPropertyRepository with property indexing
- [x] Implement SqlDelightPropertyRepository with SQL queries
- [x] Add property queries to LogseqDatabase schema
- [ ] Extract InMemoryPropertyRepository to separate file (PENDING)

### Benchmark Framework ✅
- [x] RepositoryBenchmark - Basic CRUD benchmarks
- [x] AllBackendsComparisonBenchmark - Three-backend comparison (IN_MEMORY, DATASCRIPT, SQLDELIGHT)
- [x] MicrobenchmarkRunner with statistical analysis (P50, P95, P99)
- [x] Warmup and iteration support for JIT optimization

### Build System ✅
- [x] SQLDelight 2.0 plugin integrated
- [x] Kotlin Multiplatform configured
- [x] All tests passing (./gradlew :kmp:check)
- [x] Benchmarks run successfully (./gradlew :kmp:runBackendComparison)

## Pending Tasks

### Critical Bugs (Must Fix Before Production)

#### BUG-001: Data Migration Complexity [HIGH SEVERITY]
**Status**: Investigating
**Priority**: HIGH - Blocks production migration
**Effort**: 3-4 hours (atomic tasks)
**Description**: Converting from DataScript flexible document model to relational/SQL may lose plugin query capabilities.

**Mitigation Tasks**:
- [ ] Design PluginMetadata model for plugin data storage
- [ ] Create migration utilities with data validation
- [ ] Add SQL schema for plugin_data table
- [ ] Implement integration tests

**Documentation**: docs/tasks/bug-fixes.md

#### BUG-002: Performance Regression in Graph Traversal [MEDIUM SEVERITY]
**Status**: Investigating
**Priority**: MEDIUM - User experience impact
**Effort**: 4 hours (atomic tasks)
**Description**: Kotlin stricter typing may impact graph query performance vs DataScript Datalog engine.

**Mitigation Tasks**:
- [ ] Optimize recursive CTE queries with depth limiting
- [ ] Implement query caching for frequent operations
- [ ] Benchmark and verify improvements

**Documentation**: docs/tasks/bug-fixes.md

### Feature Improvements

#### Task C: Property Repository Refactoring [2h]
**Priority**: LOW - Code quality improvement
**Effort**: 2 hours
**Description**: Extract inline InMemoryPropertyRepository to separate file for consistency.

#### Task D: Search Repository Implementation [4h]
**Priority**: MEDIUM - User-facing feature
**Effort**: 4 hours
**Description**: Implement full-text search repository using SQLite FTS5.

**Documentation**: docs/tasks/search-repository.md

## Known Issues & Risks

See docs/bugs/open/ for active bug tracking.
- HIGH: Data Migration Complexity (Bug 001) - Plugin compatibility risk
- MEDIUM: Performance Regression (Bug 002) - Query optimization concern

## Build & Test Commands

```bash
./gradlew :kmp:check
./gradlew :kmp:runBackendComparison
./gradlew :kmp:runBenchmark
```

## Architecture

Repository Layer (commonMain)
- BlockRepository: InMemory ✅, Datascript ✅, SqlDelight ✅
- PageRepository: InMemory ✅, Datascript ✅, SqlDelight ✅
- PropertyRepository: InMemory (inline), Datascript ✅, SqlDelight ✅
- ReferenceRepository: InMemory ✅, Datascript ✅, SqlDelight ✅
- SearchRepository: InMemory (planned), SqlDelight (planned)

Benchmark Layer (jvmMain)
- RepositoryBenchmark ✅
- AllBackendsComparisonBenchmark ✅
- MicrobenchmarkRunner ✅

## Next Recommended Action

### PRIMARY: Fix BUG-001 Plugin Compatibility (3-4h)

Rationale:
1. Severity: HIGH - Blocks production migration
2. Value: Plugin compatibility enables ecosystem transition
3. Dependencies: Unblocks other migration work
4. Risk: Without fix, plugins may not work post-migration
5. Effort: 3 hours (within atomic limits)

Quick Start:
1. Read docs/tasks/bug-fixes.md
2. Load context files: Models.kt, LogseqDatabase.sq, schema.cljs
3. Start with Task BUG-001-A: PluginMetadata Model Definition

### Alternative Quick Win: Property Refactoring (2h)
Extract InMemoryPropertyRepository to separate file

### Future: Search Repository (4h)
Implement full-text search with FTS5 after bug fixes

## Project Status Summary

| Metric | Value | Status |
|--------|-------|--------|
| Repository Backends | 9/9 (100%) | Complete |
| Benchmark Framework | 3/3 (100%) | Complete |
| Critical Bugs (HIGH) | 1/1 | Needs Fix |
| Medium Bugs (MEDIUM) | 1/1 | Planned |
| Refactoring Tasks | 1/1 | Pending |
| New Features | 1/1 | Planned |
| Overall | 76% | In Progress |

Estimated Remaining Effort: 9-10 hours
