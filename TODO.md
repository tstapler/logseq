# TODO.md - Logseq Kotlin Multiplatform Migration

## Current Status
- **Migration State**: 25% complete - Repository abstraction layer operational
- **Technology Stack**: Kotlin Multiplatform with SQLDelight 2.0
- **Recent Activity**: Implemented Datascript backend with Datalog indexing, comprehensive benchmarking suite

## Active Backends

### ✅ IN_MEMORY Backend
- `InMemoryBlockRepository` - Working with full CRUD
- `InMemoryPageRepository` - Working with full CRUD
- `InMemoryPropertyRepository` - Basic implementation
- `InMemoryReferenceRepository` - Basic implementation
- Performance: ~2,000-20,000 ops/sec depending on operation

### ✅ DATASCRIPT Backend  
- `DatascriptBlockRepository` - Datalog-style indexing, optimized queries
- `DatascriptPageRepository` - Datalog-style indexing, optimized queries
- Performance: ~3,000-30,000 ops/sec (15-50% faster than IN_MEMORY)

### 🔄 SQLDelight Backend
- Status: Stubs exist, needs actual SQL implementation
- Priority: HIGH - Complete three-backend comparison
- See: [docs/tasks/sqldelight-implementation.md](docs/tasks/sqldelight-implementation.md)

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
- [x] Create RepositoryFactoryImpl for backend switching

### Benchmark Framework ✅
- [x] RepositoryBenchmark - Basic CRUD benchmarks
- [x] AllBackendsComparisonBenchmark - Two-backend comparison
- [x] MicrobenchmarkRunner with statistical analysis (P50, P95, P99)
- [x] Warmup and iteration support for JIT optimization

### Build System ✅
- [x] SQLDelight 2.0 plugin integrated
- [x] Kotlin Multiplatform configured
- [x] All tests passing (`./gradlew :kmp:check`)
- [x] Benchmarks run successfully (`./gradlew :kmp:runBackendComparison`)

## Pending Tasks

### HIGH Priority
- [ ] **SQLDelight Repository Implementation** (4h) - Complete three-backend comparison
  - Location: [docs/tasks/sqldelight-implementation.md](docs/tasks/sqldelight-implementation.md)
  - Blocking: Three-way benchmark comparison

### MEDIUM Priority  
- [ ] **Reference Repository Implementation** (4h) - Track block references
  - Location: [docs/tasks/reference-repository.md](docs/tasks/reference-repository.md)
  - Enables: Reference counting, most-connected blocks

## Known Issues & Risks
- **LOW**: No critical bugs reported
- **LOW**: No high-severity issues identified
- SQLDelight implementation requires JDBC driver setup for testing

## Build & Test Commands

```bash
# Run all tests
./gradlew :kmp:check

# Run benchmark comparison
./gradlew :kmp:runBackendComparison

# Run simple benchmark
./gradlew :kmp:runBenchmark
```

## Architecture

```
Repository Layer (commonMain)
├── BlockRepository (interface)
│   ├── InMemoryBlockRepository ✅
│   ├── DatascriptBlockRepository ✅
│   └── SqlDelightBlockRepository 🔄
│
├── PageRepository (interface)
│   ├── InMemoryPageRepository ✅
│   ├── DatascriptPageRepository ✅
│   └── SqlDelightPageRepository 🔄
│
└── ReferenceRepository (interface)
    ├── InMemoryReferenceRepository 🔄
    ├── DatascriptReferenceRepository 🔄
    └── SqlDelightReferenceRepository ❌

Benchmark Layer (jvmMain)
├── RepositoryBenchmark ✅
├── AllBackendsComparisonBenchmark ✅
└── MicrobenchmarkRunner ✅
```

## Next Recommended Action

**Priority**: HIGH  
**Task**: SQLDelight Repository Implementation (4h)

**Rationale**: Complete the three-backend comparison framework by implementing SQLDelight repositories. This enables data-driven backend selection for production.

**Context Files (5)**:
1. `SqlDelightBlockRepository.kt` (NEW)
2. `SqlDelightPageRepository.kt` (NEW)
3. `LogseqDatabase.sq` (exists)
4. `LogseqDatabaseQueries.kt` (generated)
5. `RepositoryFactoryImpl.kt` (exists)

**Success Criteria**:
- Three-way benchmark runs successfully
- All existing tests pass
- `./gradlew :kmp:runBackendComparison` shows IN_MEMORY, DATASCRIPT, and SQLDELIGHT columns
