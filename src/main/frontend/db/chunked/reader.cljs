(ns frontend.db.chunked.reader
  "Chunk reading and progressive loading for chunked database storage.

   Implements 3-phase progressive loading:
   - Phase 1 (Critical <500ms): manifest + metadata + config + current month + recent pages
   - Phase 2 (Deferred): previous months + favorite pages
   - Phase 3 (On-demand): historical journals + linked pages

   Uses LRU cache for recently accessed pages."
  (:require [promesa.core :as p]))

;; =============================================================================
;; Interface Definitions (to be implemented)
;; =============================================================================

(defn restore-progressive
  "Restore a graph using progressive 3-phase loading.

   Args:
   - repo: String (repository identifier)
   - graph-name: String
   - manifest: Manifest record

   Returns: Promise<DataScript DB>

   Strategy:
   1. Load critical chunks (metadata, config, current month)
   2. Make UI interactive (return DB)
   3. Load deferred chunks in background
   4. Load remaining chunks on-demand"
  [repo graph-name manifest]
  ;; TODO: Implement in Phase 3
  (p/rejected (js/Error. "Not implemented: reader/restore-progressive")))

(defn read-chunk
  "Read and deserialize a single chunk.

   Args:
   - graph-name: String
   - chunk-key: String (e.g., 'pages/programming')
   - chunk-metadata: ChunkMetadata record

   Returns: Promise<Chunk record>

   Steps:
   1. Read compressed bytes from storage
   2. Decompress with zstd
   3. Deserialize from Transit MessagePack
   4. Verify hash
   5. Return chunk record"
  [graph-name chunk-key chunk-metadata]
  ;; TODO: Implement in Phase 3
  (p/rejected (js/Error. "Not implemented: reader/read-chunk")))

(defn read-chunk-batch
  "Read multiple chunks in parallel.

   Args:
   - graph-name: String
   - chunk-specs: [{:key String :metadata ChunkMetadata}]

   Returns: Promise<[Chunk records]>"
  [graph-name chunk-specs]
  ;; TODO: Implement in Phase 3
  (p/rejected (js/Error. "Not implemented: reader/read-chunk-batch")))

(defn get-critical-chunks
  "Identify critical chunks for Phase 1 loading.

   Args:
   - manifest: Manifest record
   - options: {:recent-pages-count 5
               :current-month-only true}

   Returns: [{:type :metadata | :config | :journal | :page
              :key String
              :metadata ChunkMetadata}]"
  [manifest & [options]]
  ;; TODO: Implement in Phase 3
  [])

(defn get-deferred-chunks
  "Identify deferred chunks for Phase 2 loading.

   Args:
   - manifest: Manifest record
   - options: {:favorite-pages [String]
               :previous-months-count 2}

   Returns: [{:type :journal | :page
              :key String
              :metadata ChunkMetadata}]"
  [manifest & [options]]
  ;; TODO: Implement in Phase 3
  [])

(defn validate-all-chunks
  "Validate all chunks for integrity.

   Args:
   - graph-name: String
   - manifest: Manifest record

   Returns: Promise<{:valid? Boolean
                     :errors [String]
                     :checked-chunks Long}>"
  [graph-name manifest]
  ;; TODO: Implement in Phase 3
  (p/rejected (js/Error. "Not implemented: reader/validate-all-chunks")))

;; =============================================================================
;; LRU Cache (to be implemented)
;; =============================================================================

(defn create-lru-cache
  "Create an LRU cache for recently accessed pages.

   Args:
   - size: Long (maximum number of cached pages)

   Returns: LRU cache instance"
  [size]
  ;; TODO: Implement in Phase 3
  nil)

(defn cache-get
  "Get a chunk from LRU cache.

   Args:
   - cache: LRU cache instance
   - chunk-key: String

   Returns: Chunk record or nil"
  [cache chunk-key]
  ;; TODO: Implement in Phase 3
  nil)

(defn cache-put
  "Put a chunk in LRU cache.

   Args:
   - cache: LRU cache instance
   - chunk-key: String
   - chunk: Chunk record

   Returns: cache (updated)"
  [cache chunk-key chunk]
  ;; TODO: Implement in Phase 3
  cache)
