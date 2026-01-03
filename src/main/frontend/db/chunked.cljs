(ns frontend.db.chunked
  "Core chunked database storage implementation.

   This namespace provides the main API for chunked database persistence,
   replacing the monolithic Transit blob approach with a chunked, lazy-loadable
   format using Transit MessagePack + zstd compression.

   Architecture:
   - Metadata chunk: Version info, statistics, schema
   - Config chunk: Built-in pages, system configuration
   - Journal chunks: Monthly aggregated journal entries
   - Page chunks: Individual pages with their blocks

   Format: Transit MessagePack + zstd compression (.msgpack.zst)
   Storage: Directory-based (Electron) or IndexedDB keys (Browser)"
  (:require [frontend.db.chunked.manifest :as manifest]
            [frontend.db.chunked.compress :as compress]
            [frontend.db.chunked.serialization :as serialization]
            [frontend.db.chunked.reader :as reader]
            [frontend.db.chunked.writer :as writer]
            [frontend.config :as config]
            [promesa.core :as p]))

;; =============================================================================
;; Data Structures
;; =============================================================================

(defrecord ChunkMetadata
  [key               ; String: chunk identifier (e.g., "pages/programming")
   size              ; Long: uncompressed size in bytes
   compressed-size   ; Long: compressed size in bytes
   hash              ; String: SHA-256 hash for integrity verification
   updated-at])      ; Inst: last modification timestamp

(defrecord Manifest
  [version             ; Long: manifest format version (currently 1)
   format-version      ; String: chunked format version (e.g., "chunked-v1")
   created-at          ; Inst: when chunked format was first created
   last-updated        ; Inst: most recent chunk update
   serialization       ; Keyword: :msgpack or :json
   compression         ; Keyword: :zstd or :gzip
   compression-level   ; Long: compression level (3 for zstd)
   transit-caching     ; Boolean: whether Transit write caching is enabled
   chunks])            ; Map: chunk metadata by type and key
                       ; {:metadata ChunkMetadata
                       ;  :config ChunkMetadata
                       ;  :journals {"2025-12" ChunkMetadata}
                       ;  :pages {"programming" ChunkMetadata}}

(defrecord PageChunk
  [page-name          ; String: normalized page name
   page-entity        ; Map: page entity (pulled with [*])
   blocks])           ; Vector: all blocks belonging to this page

(defrecord JournalChunk
  [year-month         ; String: YYYY-MM format
   date-range         ; Vector: [start-day end-day] as integers
   journals           ; Vector: journal page entities
   blocks])           ; Vector: all blocks from all journals this month

(defrecord MetadataChunk
  [schema-version     ; Long: DataScript schema version
   graph-uuid         ; UUID: unique graph identifier
   total-pages        ; Long: total number of pages
   total-blocks       ; Long: total number of blocks
   total-files        ; Long: total number of files
   created-at         ; Inst: graph creation timestamp
   statistics])       ; Map: additional statistics

(defrecord ConfigChunk
  [built-in-pages    ; Vector: default pages (TODO, DONE, etc.)
   custom-config])   ; Map: user configuration

;; =============================================================================
;; Format Detection
;; =============================================================================

(defn detect-storage-format
  "Detect whether a graph uses chunked or monolithic storage format.

   Returns:
   - {:format :chunked :version 1 :serialization :msgpack :compression :zstd}
   - {:format :monolithic}

   Note: Monolithic format will be supported permanently (no deprecation)."
  [graph-name]
  (p/let [manifest-exists? (manifest/exists? graph-name)]
    (if manifest-exists?
      (p/let [manifest-data (manifest/read graph-name)]
        {:format :chunked
         :version (:version manifest-data)
         :serialization (:serialization manifest-data)
         :compression (:compression manifest-data)})
      {:format :monolithic})))

;; =============================================================================
;; Public API
;; =============================================================================

(defn restore-chunked!
  "Restore a graph from chunked storage format.

   Loading strategy (3 phases):
   1. Critical (<500ms): manifest + metadata + config + current month + recent pages
   2. Deferred (background): previous months + favorite pages
   3. On-demand: historical journals + linked pages

   Returns: Promise<DataScript DB>"
  [repo]
  (p/let [graph-name (config/get-repo-dir repo)
          manifest-data (manifest/read graph-name)]
    (reader/restore-progressive repo graph-name manifest-data)))

(defn persist-chunked-incremental!
  "Persist only changed chunks after a transaction.

   Strategy:
   1. Identify affected pages/journals from tx-data
   2. Extract and serialize changed chunks
   3. Compress with zstd
   4. Save changed chunks atomically
   5. Update manifest

   Returns: Promise<void>"
  [repo db-after tx-meta]
  (p/let [graph-name (config/get-repo-dir repo)
          tx-data (:tx-data tx-meta)
          changed-chunks (writer/identify-changed-chunks db-after tx-data)]
    (writer/save-incremental repo graph-name db-after changed-chunks)))

(defn migrate-to-chunked!
  "Migrate a graph from monolithic to chunked storage format.

   Strategy:
   1. Load full monolithic database
   2. Extract all chunks (metadata, config, journals, pages)
   3. Serialize and compress each chunk
   4. Save all chunks
   5. Create manifest
   6. Keep old format as backup

   Returns: Promise<void>"
  [repo db]
  (p/let [graph-name (config/get-repo-dir repo)]
    (writer/migrate-full-graph repo graph-name db)))

(defn rebuild-index!
  "Rebuild chunked index from scratch (re-index).

   Used for:
   - Corruption recovery
   - Format upgrades
   - Manual optimization

   Returns: Promise<void>"
  [repo db]
  (migrate-to-chunked! repo db))

;; =============================================================================
;; Validation & Diagnostics
;; =============================================================================

(defn validate-chunks
  "Validate all chunks for a graph.

   Checks:
   - Manifest integrity
   - Chunk hashes match content
   - No missing referenced chunks
   - Compression format validity

   Returns: Promise<{:valid? boolean :errors [string]}>"
  [graph-name]
  (p/let [manifest-data (manifest/read graph-name)
          validation-results (reader/validate-all-chunks graph-name manifest-data)]
    validation-results))

(defn get-storage-stats
  "Get storage statistics for a graph.

   Returns: Promise<{:total-size Long
                     :compressed-size Long
                     :compression-ratio Float
                     :chunk-count Long
                     :largest-chunk ChunkMetadata}>"
  [graph-name]
  (p/let [manifest-data (manifest/read graph-name)]
    (manifest/compute-stats manifest-data)))
