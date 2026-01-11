# Testing and Validation Tasks - Chunked Database Storage

**Epic**: Testing & Benchmarking (Phase 5)
**Status**: 20% Complete
**Estimated Time**: 15 hours serial, 10 hours with parallelization
**Priority**: HIGHEST - Validates 85% of existing implementation

---

## Overview

Core implementation is 85% complete (~1,670 LOC) but undertested. This epic expands test coverage from ~20% to 90%+ and validates integration wiring before activation.

**Current Test Status**:
- 6 test files exist (~250 LOC)
- Basic roundtrip tests implemented
- Integration test scaffold created
- **Gap**: Only ~20% of target 200+ tests

**Success Criteria**:
- 150+ unit tests across all modules
- 25+ integration tests for end-to-end flows
- 90%+ code coverage
- All integration paths verified working

---

## Task 1.1: Verify Integration Wiring

**Independent**: No dependencies, standalone verification
**Negotiable**: Can trace manually or add instrumentation
**Valuable**: Confirms feature is ready for activation
**Estimable**: 2 hours with high confidence
**Small**: Single responsibility - verify integration paths
**Testable**: Can confirm restore/persist paths called

### Context Boundary
- **Files**: 3 (db.cljs, handler.cljs, state.cljs)
- **Total LOC**: ~400 relevant lines
- **Time Limit**: 2 hours
- **Concepts**: 1 primary (integration flow tracing)

### Prerequisites
None - can start immediately

### Atomic Steps

1. **Add console logging to restore path** (15 min)
   - File: `src/main/frontend/db.cljs`
   - Function: `start-db-conn!` (line 162-171)
   - Add: `(log/info "Chunked storage check:" chunked-exists?)`
   - Function: `restore-with-chunked-support!` (line 126-143)
   - Add: `(log/info "Using chunked restore for" repo)`

2. **Add console logging to persist path** (15 min)
   - File: `src/main/frontend/db.cljs`
   - Function: `persist!` (line 157-160)
   - Add: `(log/info "Persist called for" repo)`
   - Function: `persist-with-chunked-support!` (line 145-155)
   - Add: `(log/info "Using chunked persist:" use-chunked?)`

3. **Test Electron restore path** (30 min)
   - Create test graph in Electron
   - Enable chunked storage via localStorage
   - Restart application
   - Check console logs for "Using chunked restore"
   - Verify restore-chunked-graph! called

4. **Test browser restore path** (30 min)
   - Create test graph in browser
   - Enable chunked storage via localStorage
   - Reload page
   - Check console logs for "Using chunked restore"
   - Verify IndexedDB used for chunk storage

5. **Test persist path** (15 min)
   - Edit a page in test graph
   - Check console logs for "Using chunked persist"
   - Verify persist-chunked-graph! called
   - Verify only changed chunk saved

6. **Document integration flow** (15 min)
   - Create flow diagram showing:
     - Startup → start-db-conn! → should-use-chunked-storage? → restore path
     - Transaction → persist! → should-use-chunked-storage? → persist path
   - Add comments to db.cljs documenting flow
   - Update docs/analysis/ with findings

### Validation Checklist

- [ ] Console shows "Using chunked restore" when enabled
- [ ] Console shows "Using monolithic restore" when disabled
- [ ] Electron path verified (IPC calls logged)
- [ ] Browser path verified (IndexedDB calls logged)
- [ ] Persist path verified (incremental save logged)
- [ ] Documentation updated with flow diagram

### Expected Findings

Integration is already wired correctly:
- `start-db-conn!` checks both monolithic and chunked existence
- Calls `restore-with-chunked-support!` which auto-detects format
- `persist!` calls `persist-with-chunked-support!` which auto-detects format
- Feature flag in localStorage: `"{repo}-chunked-enabled"`
- Manifest existence also triggers chunked path

**Potential Issue**: If logs don't show chunked path, check:
- Is localStorage flag set correctly?
- Does manifest exist in expected location?
- Is `should-use-chunked-storage?` returning correct value?

### Deliverables

1. Console logs confirming integration paths
2. Flow diagram in docs/analysis/
3. Comments in db.cljs documenting flow
4. Verification report (pass/fail for each path)

---

## Task 1.2: Expand Compression Test Coverage

**Independent**: No coordination required
**Negotiable**: Can test different scenarios
**Valuable**: Ensures compression reliability
**Estimable**: 2 hours
**Small**: Single module focus
**Testable**: Coverage metrics available

### Context Boundary
- **Files**: 2 (compress_test.cljs, compress.cljs)
- **Total LOC**: ~250 relevant lines
- **Time Limit**: 2 hours
- **Concepts**: 1 primary (compression reliability)

### Prerequisites
None - compression module is complete

### Atomic Steps

1. **Roundtrip tests for all data types** (30 min)
   ```clojure
   (deftest compress-decompress-uint8array
     (let [data (js/Uint8Array. [1 2 3 4 5])
           compressed (compress/compress data)
           decompressed (compress/decompress compressed)]
       (is (= data decompressed))))

   ;; Add similar tests for:
   ;; - ArrayBuffer
   ;; - Buffer (Node.js)
   ;; - String (UTF-8, unicode, emoji)
   ;; - Large data (>1MB)
   ;; - Empty data
   ```

2. **Error handling tests** (30 min)
   ```clojure
   (deftest compress-handles-invalid-input
     (is (thrown? js/Error (compress/compress nil)))
     (is (thrown? js/Error (compress/compress js/undefined))))

   (deftest decompress-handles-corrupted-data
     (let [corrupted (js/Uint8Array. [255 255 255])]
       (is (thrown? js/Error (compress/decompress corrupted)))))

   (deftest handles-wasm-initialization-failure
     ;; Mock WASM failure
     (with-redefs [zstd/init (fn [] (p/rejected (js/Error. "WASM failed")))]
       (is (= :gzip @compress/*compression-backend*))))
   ```

3. **Compression ratio validation** (20 min)
   ```clojure
   (deftest compression-ratio-meets-targets
     (let [test-data (generate-typical-datascript-data 100kb)
           compressed (compress/compress test-data)
           ratio (/ (.-length compressed) (.-length test-data))]
       (is (< ratio 0.3) "Compression ratio should be <30%")))
   ```

4. **Performance tests** (20 min)
   ```clojure
   (deftest compression-speed-meets-targets
     (let [test-data (js/Uint8Array. (repeat 1000000 65))
           start (js/performance.now)
           _ (compress/compress test-data)
           elapsed (- (js/performance.now) start)
           speed (/ 1.0 elapsed)] ; MB/s
       (is (> speed 100) "Compression should be >100 MB/s")))
   ```

5. **Edge case tests** (20 min)
   - Empty data → empty compressed data
   - Single byte → compresses to >1 byte (overhead)
   - Highly repetitive data → high compression ratio
   - Random data → low compression ratio
   - Unicode strings → preserve all characters
   - Large data (10MB) → doesn't crash

### Target Test Count
20+ tests achieving 95% coverage of compress.cljs

### Validation Checklist

- [ ] All data types roundtrip correctly
- [ ] Invalid input throws clear errors
- [ ] WASM failure falls back to gzip
- [ ] Compression ratio >70% on typical data
- [ ] Compression speed >100 MB/s
- [ ] Decompression speed >300 MB/s
- [ ] Edge cases handled gracefully
- [ ] Coverage report shows ≥95%

### Deliverables

1. 20+ passing tests in compress_test.cljs
2. Coverage report showing 95%+
3. Performance benchmark results
4. Documented edge cases and limitations

---

## Task 1.3: Expand Serialization Test Coverage

**Independent**: No dependencies
**Negotiable**: Test scenarios flexible
**Valuable**: Ensures data integrity
**Estimable**: 2 hours
**Small**: Single module
**Testable**: Coverage metrics

### Context Boundary
- **Files**: 2 (serialization_test.cljs, serialization.cljs)
- **Total LOC**: ~330 relevant lines
- **Time Limit**: 2 hours
- **Concepts**: 1 primary (serialization correctness)

### Prerequisites
None - serialization module is complete

### Atomic Steps

1. **Transit MessagePack roundtrip tests** (30 min)
   ```clojure
   (deftest serialize-deserialize-basic-types
     (doseq [value [nil true false 42 3.14 "hello" :keyword]]
       (is (= value (-> value ser/serialize ser/deserialize)))))

   (deftest serialize-deserialize-collections
     (let [data {:a [1 2 3] :b #{:x :y} :c '(1 2 3)}]
       (is (= data (-> data ser/serialize ser/deserialize)))))
   ```

2. **Transit caching validation** (30 min)
   ```clojure
   (deftest transit-caching-reduces-size
     (let [data (repeat 100 {:name "test" :type :page})
           without-cache (ser/serialize-no-cache data)
           with-cache (ser/serialize data)]
       (is (< (count with-cache) (count without-cache))
           "Caching should reduce size for repeated keys")))
   ```

3. **DataScript type tests** (40 min)
   ```clojure
   (deftest serialize-datascript-entity
     (let [entity {:db/id 123
                   :block/uuid (random-uuid)
                   :block/content "test"
                   :block/created-at (js/Date.)}]
       (is (= entity (-> entity ser/serialize ser/deserialize)))))

   (deftest serialize-datascript-refs
     (let [entity {:db/id 123
                   :block/page {:db/id 456}
                   :block/refs [{:db/id 789} {:db/id 101112}]}]
       (is (= entity (-> entity ser/serialize ser/deserialize)))))
   ```

4. **Format detection tests** (10 min)
   ```clojure
   (deftest detect-msgpack-format
     (let [msgpack-data (ser/serialize {:test true})]
       (is (= :msgpack (ser/detect-format msgpack-data)))))

   (deftest detect-json-format
     (let [json-data (.stringify js/JSON (clj->js {:test true}))]
       (is (= :json (ser/detect-format json-data)))))
   ```

5. **Error handling tests** (10 min)
   ```clojure
   (deftest handles-invalid-transit-data
     (is (thrown? js/Error (ser/deserialize (js/Uint8Array. [255 255])))))

   (deftest handles-nil-input
     (is (nil? (ser/serialize nil))))
   ```

### Target Test Count
30+ tests achieving 95% coverage of serialization.cljs

### Validation Checklist

- [ ] All basic types serialize correctly
- [ ] Collections serialize correctly
- [ ] Transit caching reduces size by 15-20%
- [ ] DataScript entities roundtrip with all fields
- [ ] DataScript refs preserved correctly
- [ ] UUIDs, dates, keywords handled correctly
- [ ] Format detection works (MessagePack vs JSON)
- [ ] Error handling robust
- [ ] Coverage report shows ≥95%

### Deliverables

1. 30+ passing tests in serialization_test.cljs
2. Coverage report showing 95%+
3. Caching effectiveness benchmark
4. DataScript type compatibility matrix

---

## Task 1.4: Expand Reader Test Coverage

**Independent**: Can mock storage layer
**Negotiable**: Test scenarios flexible
**Valuable**: Critical for progressive loading
**Estimable**: 3 hours
**Small**: Single module
**Testable**: Coverage metrics

### Context Boundary
- **Files**: 2 (reader_test.cljs, reader.cljs)
- **Total LOC**: ~470 relevant lines
- **Time Limit**: 3 hours
- **Concepts**: 2 (progressive loading + LRU cache)

### Prerequisites
None - reader module is complete

### Atomic Steps

1. **Progressive loading phase tests** (45 min)
   ```clojure
   (deftest phase-1-loads-critical-chunks
     (with-mock-storage
       (let [chunks (reader/load-phase-1 "test-graph")]
         (is (contains? chunks :manifest))
         (is (contains? chunks :metadata))
         (is (contains? chunks :config))
         (is (contains? chunks :current-journal))
         (is (= 5 (count (:recent-pages chunks)))))))

   (deftest phase-2-loads-deferred-chunks
     ;; Test background loading doesn't block
     (let [promise (reader/load-phase-2-async "test-graph")]
       (is (= :pending (.-state promise)))
       ;; Wait for completion
       (is (contains? @promise :previous-journals))))
   ```

2. **LRU cache tests** (45 min)
   ```clojure
   (deftest lru-cache-evicts-oldest
     (let [cache (reader/create-lru-cache 3)]
       (reader/cache-put cache "a" {:data 1})
       (reader/cache-put cache "b" {:data 2})
       (reader/cache-put cache "c" {:data 3})
       (reader/cache-put cache "d" {:data 4})
       (is (nil? (reader/cache-get cache "a")) "Oldest should be evicted")
       (is (= {:data 4} (reader/cache-get cache "d")))))

   (deftest lru-cache-hit-rate-tracked
     (let [cache (reader/create-lru-cache 10)]
       (dotimes [i 5] (reader/cache-put cache (str i) {:data i}))
       (dotimes [_ 3] (reader/cache-get cache "0")) ; 3 hits
       (dotimes [_ 2] (reader/cache-get cache "999")) ; 2 misses
       (is (= 0.6 (reader/cache-hit-rate cache)))))
   ```

3. **Parallel loading tests** (30 min)
   ```clojure
   (deftest parallel-chunk-loading-works
     (with-mock-storage
       (let [chunk-keys ["pages/a" "pages/b" "pages/c"]
             start (js/performance.now)
             chunks (reader/read-chunk-batch "test-graph" chunk-keys)
             elapsed (- (js/performance.now) start)]
         (is (= 3 (count chunks)))
         (is (< elapsed 100) "Parallel should be faster than 3x serial"))))
   ```

4. **Validation tests** (30 min)
   ```clojure
   (deftest validate-chunk-integrity
     (with-mock-storage
       (let [chunk {:key "pages/test" :hash "abc123" :data "..."}]
         (is (reader/validate-chunk chunk))
         ;; Corrupt data
         (is (not (reader/validate-chunk (assoc chunk :data "corrupted")))))))

   (deftest validate-all-chunks-finds-issues
     (with-mock-storage
       (setup-graph-with-corrupted-chunk)
       (let [result (reader/validate-all-chunks "test-graph")]
         (is (false? (:valid? result)))
         (is (= 1 (count (:errors result)))))))
   ```

5. **Database merging tests** (30 min)
   ```clojure
   (deftest merge-chunks-into-db-preserves-refs
     (let [page-chunk {:page-name "test" :blocks [...]}
           db (d/empty-db db-schema)
           merged (reader/merge-chunks-into-db db [page-chunk])]
       (is (= 1 (count (d/q '[:find ?e :where [?e :block/page]] merged))))))
   ```

### Target Test Count
40+ tests achieving 90% coverage of reader.cljs

### Validation Checklist

- [ ] Phase 1 loads critical chunks in correct order
- [ ] Phase 2 loads in background without blocking
- [ ] Phase 3 on-demand loading works
- [ ] LRU cache evicts correctly (FIFO for same access time)
- [ ] LRU cache tracks hit rate accurately
- [ ] Parallel loading faster than serial
- [ ] Chunk validation detects corruption
- [ ] Database merging preserves all refs
- [ ] Coverage report shows ≥90%

### Deliverables

1. 40+ passing tests in reader_test.cljs
2. Coverage report showing 90%+
3. LRU cache performance analysis
4. Progressive loading timing breakdown

---

## Task 1.5: Expand Writer Test Coverage

**Independent**: Can mock storage
**Negotiable**: Test scenarios flexible
**Valuable**: Critical for incremental saves
**Estimable**: 3 hours
**Small**: Single module
**Testable**: Coverage metrics

### Context Boundary
- **Files**: 2 (writer_test.cljs, writer.cljs)
- **Total LOC**: ~530 relevant lines
- **Time Limit**: 3 hours
- **Concepts**: 2 (change detection + chunk extraction)

### Prerequisites
None - writer module is complete

### Atomic Steps

1. **Change detection tests** (45 min)
   ```clojure
   (deftest identify-changed-page-chunks
     (let [tx-data [{:db/id 123 :block/page {:block/name "test"} ...}]
           changed (writer/identify-changed-chunks db tx-data)]
       (is (contains? (:pages changed) "test"))
       (is (= 1 (count (:pages changed))))))

   (deftest identify-changed-journal-chunks
     (let [tx-data [{:db/id 456 :block/journal-day 20261203 ...}]
           changed (writer/identify-changed-chunks db tx-data)]
       (is (contains? (:journals changed) "2026-12"))))

   (deftest no-false-positives-in-change-detection
     (let [tx-data [{:db/id 789 :block/content "new content"}]
           changed (writer/identify-changed-chunks db tx-data)]
       ;; Should not trigger metadata chunk update unless stats changed
       (is (not (contains? changed :metadata)))))
   ```

2. **Chunk extraction tests** (60 min)
   ```clojure
   (deftest extract-page-chunk-complete
     (let [db (setup-test-db-with-page "test-page")
           chunk (writer/extract-page-chunk db "test-page")]
       (is (= "test-page" (:page-name chunk)))
       (is (some? (:page-entity chunk)))
       (is (vector? (:blocks chunk)))
       (is (= 5 (count (:blocks chunk))))))

   (deftest extract-journal-chunk-month
     (let [db (setup-test-db-with-journals "2026-12")
           chunk (writer/extract-journal-chunk db "2026-12")]
       (is (= "2026-12" (:year-month chunk)))
       (is (= [20261201 20261231] (:date-range chunk)))
       (is (= 31 (count (:journals chunk))))))

   (deftest extract-metadata-chunk-accurate
     (let [db (setup-test-db-with-100-pages)
           chunk (writer/extract-metadata-chunk db)]
       (is (= 100 (:total-pages chunk)))
       (is (= 500 (:total-blocks chunk)))
       (is (uuid? (:graph-uuid chunk)))))

   (deftest extract-config-chunk-includes-built-in-pages
     (let [db (setup-test-db)
           chunk (writer/extract-config-chunk db)]
       (is (contains? (set (:built-in-pages chunk)) "TODO"))
       (is (contains? (set (:built-in-pages chunk)) "DONE"))))
   ```

3. **Incremental save tests** (45 min)
   ```clojure
   (deftest incremental-save-only-changed-chunks
     (with-mock-storage
       (let [db (setup-test-db-with-100-pages)
             _ (writer/migrate-full-graph "test-repo" "test-graph" db)
             _ (reset-mock-storage-write-count)
             ;; Edit single page
             db-after (transact-edit-page db "page-42")
             tx-data (get-last-tx-data)
             _ (writer/save-incremental "test-repo" "test-graph" db-after
                                        (writer/identify-changed-chunks db-after tx-data))]
         ;; Should only write 2 chunks: page-42 + manifest
         (is (= 2 (get-mock-storage-write-count))))))
   ```

4. **Migration tests** (30 min)
   ```clojure
   (deftest migrate-full-graph-preserves-block-count
     (with-mock-storage
       (let [db (setup-test-db-with-100-pages-500-blocks)
             _ (writer/migrate-full-graph "test-repo" "test-graph" db)
             manifest (manifest/read "test-graph")
             metadata-chunk (read-and-deserialize-chunk "test-graph" "metadata")]
         (is (= 500 (:total-blocks metadata-chunk))))))

   (deftest migration-creates-all-chunk-types
     (with-mock-storage
       (let [db (setup-test-db)
             _ (writer/migrate-full-graph "test-repo" "test-graph" db)
             manifest (manifest/read "test-graph")]
         (is (contains? (:chunks manifest) :metadata))
         (is (contains? (:chunks manifest) :config))
         (is (contains? (:chunks manifest) :journals))
         (is (contains? (:chunks manifest) :pages)))))
   ```

### Target Test Count
40+ tests achieving 90% coverage of writer.cljs

### Validation Checklist

- [ ] Change detection accurate (no false positives/negatives)
- [ ] Page chunk extraction includes all blocks
- [ ] Journal chunk extraction aggregates by month
- [ ] Metadata chunk has accurate statistics
- [ ] Config chunk includes built-in pages
- [ ] Incremental save only writes changed chunks
- [ ] Migration preserves all data (block count matches)
- [ ] Parallel saves work correctly
- [ ] Coverage report shows ≥90%

### Deliverables

1. 40+ passing tests in writer_test.cljs
2. Coverage report showing 90%+
3. Change detection accuracy analysis
4. Migration performance benchmarks

---

## Task 1.6: E2E Integration Tests

**Independent**: Uses test fixtures
**Negotiable**: Can test different scenarios
**Valuable**: Validates end-to-end flow
**Estimable**: 3 hours
**Small**: Single test file
**Testable**: Pass/fail criteria clear

### Context Boundary
- **Files**: 3 (chunked_integration_test.cljs, test fixtures, mock storage)
- **Total LOC**: ~200 relevant lines
- **Time Limit**: 3 hours
- **Concepts**: 1 primary (end-to-end flow validation)

### Prerequisites
Tasks 1.2-1.5 (module tests provide utilities and confidence)

### Atomic Steps

1. **Create test fixture** (30 min)
   ```clojure
   (defn create-test-graph-fixture
     "Create a test graph with realistic data structure:
     - 50 pages (various sizes)
     - 3 months of journals
     - Cross-references between pages
     - Metadata with statistics"
     []
     (let [db (d/empty-db schema)]
       ;; Add pages
       (doseq [i (range 50)]
         (transact-page db (str "page-" i) (generate-blocks i)))
       ;; Add journals
       (doseq [month ["2026-10" "2026-11" "2026-12"]]
         (transact-journal-month db month))
       db))
   ```

2. **Test full save/load cycle** (45 min)
   ```clojure
   (deftest full-save-load-cycle-preserves-data
     (with-mock-storage
       (let [original-db (create-test-graph-fixture)
             repo "test-repo"
             graph-name "test-graph"
             ;; Save as chunked
             _ (writer/migrate-full-graph repo graph-name original-db)
             ;; Verify manifest exists
             manifest (manifest/read graph-name)
             _ (is (some? manifest))
             ;; Load back
             restored-db @(reader/restore-progressive repo graph-name manifest)
             ;; Compare
             original-blocks (count-blocks original-db)
             restored-blocks (count-blocks restored-db)]
         (is (= original-blocks restored-blocks)
             "Block count should match after save/load cycle"))))
   ```

3. **Test incremental save** (30 min)
   ```clojure
   (deftest incremental-save-only-updates-changed-chunk
     (with-mock-storage
       (let [db (create-test-graph-fixture)
             repo "test-repo"
             graph-name "test-graph"
             ;; Initial migration
             _ (writer/migrate-full-graph repo graph-name db)
             _ (reset-mock-storage-writes)
             ;; Edit single page
             tx-data [{:db/id 123 :block/content "UPDATED"}]
             db-after (transact db tx-data)
             changed (writer/identify-changed-chunks db-after tx-data)
             _ (writer/save-incremental repo graph-name db-after changed)
             writes (get-mock-storage-writes)]
         ;; Should only write: changed page chunk + manifest
         (is (<= (count writes) 3)
             "Incremental save should write minimal chunks"))))
   ```

4. **Test migration** (30 min)
   ```clojure
   (deftest migration-monolithic-to-chunked-lossless
     (with-mock-storage
       (let [db (create-test-graph-fixture)
             repo "test-repo"
             db-name (str repo "_datascript")
             ;; Save as monolithic first
             monolithic-str (dt/write-transit-str db)
             _ (db-persist/save-graph! db-name monolithic-str)
             ;; Migrate to chunked
             _ (chunked/migrate-to-chunked! repo db)
             ;; Verify both formats coexist
             manifest (manifest/exists? (str repo "_chunked"))
             _ (is (true? @manifest))
             ;; Load from chunked format
             restored @(chunked/restore-chunked! repo)
             ;; Compare block counts
             original-count (count-blocks db)
             restored-count (count-blocks restored)]
         (is (= original-count restored-count)
             "Migration should preserve all blocks"))))
   ```

5. **Test format detection** (20 min)
   ```clojure
   (deftest format-detection-selects-correct-path
     (with-mock-storage
       ;; Test 1: Manifest exists → use chunked
       (let [_ (create-mock-manifest "graph1")]
         (is (= :chunked (:format @(chunked/detect-storage-format "graph1")))))

       ;; Test 2: No manifest, flag enabled → use chunked
       (with-redefs [state/chunked-storage-enabled? (constantly true)]
         (is (= :chunked (:format @(chunked/detect-storage-format "graph2")))))

       ;; Test 3: No manifest, flag disabled → use monolithic
       (with-redefs [state/chunked-storage-enabled? (constantly false)]
         (is (= :monolithic (:format @(chunked/detect-storage-format "graph3")))))))
   ```

6. **Test error recovery** (45 min)
   ```clojure
   (deftest handles-missing-chunk-gracefully
     (with-mock-storage
       (let [db (create-test-graph-fixture)
             _ (writer/migrate-full-graph "repo" "graph" db)
             ;; Delete a chunk
             _ (delete-mock-chunk "graph" "pages/page-42")
             ;; Try to load
             restored @(reader/restore-progressive "repo" "graph" (manifest/read "graph"))]
         ;; Should load other chunks successfully
         (is (some? restored))
         ;; Missing page should be logged as error
         (is (logged? :error "Missing chunk: pages/page-42")))))

   (deftest handles-corrupted-manifest
     (with-mock-storage
       (let [_ (create-test-graph-fixture-with-chunks "graph")
             ;; Corrupt manifest
             _ (corrupt-mock-chunk "graph" "manifest")
             ;; Try to read - should fall back to reconstruction
             manifest (manifest/read-with-recovery "graph")]
         (is (some? manifest))
         (is (logged? :warn "Manifest corrupted, attempting reconstruction")))))
   ```

### Target Test Count
25+ integration tests covering all critical paths

### Validation Checklist

- [ ] Full save/load cycle preserves all data
- [ ] Block count identical before/after
- [ ] Incremental save only writes changed chunks (2-3 max)
- [ ] Migration from monolithic to chunked lossless
- [ ] Format detection works in all scenarios
- [ ] Missing chunks handled gracefully (log error, continue)
- [ ] Corrupted manifest recoverable from chunks
- [ ] Concurrent access doesn't corrupt data (if testable)
- [ ] All tests pass consistently (10 runs)

### Deliverables

1. 25+ passing integration tests
2. Test fixture generator for realistic graphs
3. Mock storage implementation for testing
4. Error recovery validation report

---

## Parallelization Strategy

**Phase 1: Parallel Execution** (after Task 1.1)
- Task 1.2 (Compression tests)
- Task 1.3 (Serialization tests)
- Task 1.4 (Reader tests)
- Task 1.5 (Writer tests)

These 4 tasks are fully independent and can run in parallel.
- **Serial time**: 10 hours
- **Parallel time**: 3 hours (longest task: 1.4 or 1.5)
- **Time saved**: 7 hours

**Phase 2: Sequential Execution**
- Task 1.6 (Integration tests) - depends on utilities from 1.2-1.5

---

## Total Time Estimate

- **Serial execution**: 15 hours
- **With parallelization**: 2h (1.1) + 3h (1.2-1.5 parallel) + 3h (1.6) = **8 hours**
- **Time saved**: 7 hours (47% reduction)

---

## Success Metrics

- **Test Count**: 150+ unit tests, 25+ integration tests (target: 175+ total)
- **Coverage**: ≥90% across all modules
- **Pass Rate**: 100% consistently
- **Performance**: All benchmarks within targets
- **Documentation**: All edge cases documented

---

## Next Steps

After completing this epic:
1. Proceed to Epic 2: Performance Validation
2. Use test infrastructure for benchmarking
3. Validate performance claims (92% startup improvement, 82% storage reduction)
4. Document results for user-facing documentation
