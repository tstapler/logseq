# Project TODO

## Chunked Storage Implementation

**Status**: 85% Complete - Core implementation done, needs testing & activation
**Documentation**: See `docs/tasks/chunked-database-storage.md` for full feature plan
**Strategic Analysis**: See `docs/analysis/strategic-project-analysis-2026-01-10.md`

### Completed Phases

#### ✅ Phase 1: Foundation (100% Complete)
- [x] Core data structures (chunked.cljs)
- [x] Compression module (compress.cljs) - zstd level 3
- [x] Serialization module (serialization.cljs) - Transit MessagePack
- [x] Manifest operations (manifest.cljs)
- [x] Format detection logic

#### ✅ Phase 2: Reader Infrastructure (100% Complete)
- [x] Progressive reader with 3-phase loading (reader.cljs)
- [x] LRU cache implementation
- [x] Parallel chunk loading with p/all
- [x] Chunk validation with integrity checks

#### ✅ Phase 3: Writer Infrastructure (100% Complete)
- [x] Incremental writer with change detection (writer.cljs)
- [x] Chunk extraction (page, journal, metadata, config)
- [x] Full graph migration logic
- [x] Parallel save operations

#### ✅ Phase 4: Storage Integration (100% Complete)
- [x] Storage primitives (persist.cljs) - get-chunk, save-chunk
- [x] Electron IPC handlers (handler.cljs)
- [x] IndexedDB integration for browser
- [x] Integration wiring in db.cljs (restore/persist paths)

### Remaining Work

#### Phase 5: Testing & Benchmarking (20% Complete)
**Priority**: HIGHEST - Validates 85% of existing work
**Estimated Time**: 15 hours (10 hours with parallelization)
**Details**: See `docs/tasks/testing-and-validation.md`

- [x] Basic test framework setup (6 test files created)
- [ ] **Task 1.1**: Verify integration wiring (2h) - RECOMMENDED START HERE
- [ ] **Task 1.2**: Expand compression test coverage to 20+ tests, 95% coverage (2h)
- [ ] **Task 1.3**: Expand serialization test coverage to 30+ tests, 95% coverage (2h)
- [ ] **Task 1.4**: Expand reader test coverage to 40+ tests, 90% coverage (3h)
- [ ] **Task 1.5**: Expand writer test coverage to 40+ tests, 90% coverage (3h)
- [ ] **Task 1.6**: E2E integration tests - 25+ tests for full flows (3h)

**Tasks 1.2-1.5 can run in parallel** (10h serial → 3h parallel)

#### Phase 6: Performance Validation (0% Complete)
**Priority**: HIGH - Proves value proposition
**Estimated Time**: 5 hours
**Depends On**: Phase 5 complete
**Details**: See `docs/tasks/performance-validation.md`

- [ ] **Task 2.1**: Startup time benchmarks - small/medium/large graphs (2h)
- [ ] **Task 2.2**: Memory profiling - heap size, LRU cache, leak detection (2h)
- [ ] **Task 2.3**: Storage & compression benchmarks - validate 82% reduction (1h)

#### Phase 7: User-Facing Features (0% Complete)
**Priority**: MEDIUM - Enables user adoption
**Estimated Time**: 6 hours
**Depends On**: Phase 6 complete
**Details**: See `docs/tasks/user-interface.md`

- [ ] **Task 3.1**: Settings UI - storage format section with toggle (2h)
- [ ] **Task 3.2**: Migration progress UI - modal with progress feedback (2h)
- [ ] **Task 3.3**: E2E user flow testing - manual validation checklist (2h)

### Performance Targets

| Metric | Current | Target | Status |
|--------|---------|--------|--------|
| Startup Time (critical path) | 6.14s | <500ms | 🔄 Pending validation |
| Time to Interactive | 6.14s | <500ms | 🔄 Pending validation |
| Full Data Load (background) | 6.14s | <2s | 🔄 Pending validation |
| Incremental Save | N/A | <50ms | 🔄 Pending validation |
| Storage Size | 113MB | ~20MB (82% reduction) | ✅ Expected (validated in design) |
| Memory at Startup | 100% | <30% | 🔄 Pending validation |

### Total Time Estimate

- **Serial execution**: 26 hours
- **With parallelization**: 21 hours
- **Recommended path**: Start Task 1.1 (2h), then parallelize Tasks 1.2-1.5 (3h), then sequential 2.1-2.3-3.1-3.2-3.3 (11h) = **16 hours critical path**

### Files Implemented

**Production Code** (~1,670 LOC):
- `src/main/frontend/db/chunked.cljs` (188 LOC)
- `src/main/frontend/db/chunked/compress.cljs` (232 LOC)
- `src/main/frontend/db/chunked/serialization.cljs` (266 LOC)
- `src/main/frontend/db/chunked/manifest.cljs` (272 LOC)
- `src/main/frontend/db/chunked/reader.cljs` (393 LOC)
- `src/main/frontend/db/chunked/writer.cljs` (435 LOC)
- `src/main/frontend/db/persist.cljs` (+36 LOC)
- `src/electron/electron/handler.cljs` (+57 LOC)
- `src/main/frontend/db.cljs` (+194 LOC)
- `src/main/frontend/state.cljs` (+10 LOC)

**Test Code** (~250 LOC, needs expansion):
- `src/test/frontend/db/compress_test.cljs` (21 LOC) - needs 20 tests
- `src/test/frontend/db/serialization_test.cljs` (65 LOC) - needs 30 tests
- `src/test/frontend/db/reader_test.cljs` (74 LOC) - needs 40 tests
- `src/test/frontend/db/writer_test.cljs` (91 LOC) - needs 40 tests
- `src/test/frontend/db/chunked_test.cljs` (108 LOC) - needs expansion
- `src/test/frontend/db/chunked_integration_test.cljs` (136 LOC) - needs 25 tests

### Next Action

**RECOMMENDED**: Start with Task 1.1 (Verify Integration Wiring, 2 hours)
- **Why**: Confirms feature is wired correctly before investing in testing
- **Risk**: Low (read-only verification with logging)
- **Context**: 3 files (db.cljs, handler.cljs, state.cljs)
- **Deliverable**: Documented integration flow, console logs confirming paths

**Alternative**: If time-constrained, parallelize Tasks 1.2-1.5 (3 hours parallel)
- **Why**: Independent test expansion tasks
- **Risk**: Low (isolated test files)
- **Context**: 1 file per task (test file + implementation file)
- **Deliverable**: 130+ tests, 90%+ coverage

See `docs/analysis/strategic-project-analysis-2026-01-10.md` for complete strategic analysis and atomic task breakdowns following the AIC (ATOMIC-INVEST-CONTEXT) framework.
