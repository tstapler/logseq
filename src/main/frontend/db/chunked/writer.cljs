(ns frontend.db.chunked.writer
  "Chunk writing and incremental persistence for chunked database storage.

   Implements incremental save strategy:
   1. Identify changed chunks from transaction data
   2. Extract and serialize only changed chunks
   3. Compress with zstd
   4. Save atomically using .tmp → rename pattern
   5. Update manifest

   Provides full migration from monolithic format."
  (:require [promesa.core :as p]
            [datascript.core :as d]))

;; =============================================================================
;; Interface Definitions (to be implemented)
;; =============================================================================

(defn identify-changed-chunks
  "Identify which chunks were affected by a transaction.

   Args:
   - db: DataScript DB (db-after)
   - tx-data: Transaction data from tx-meta

   Returns: {:pages #{String}      ; page names that changed
             :journals #{String}}   ; year-months that changed (YYYY-MM)"
  [db tx-data]
  ;; TODO: Implement in Phase 4
  {:pages #{}
   :journals #{}})

(defn extract-page-chunk
  "Extract all data for a page chunk.

   Args:
   - db: DataScript DB
   - page-name: String

   Returns: PageChunk record"
  [db page-name]
  ;; TODO: Implement in Phase 4
  (throw (js/Error. "Not implemented: writer/extract-page-chunk")))

(defn extract-journal-chunk
  "Extract all data for a journal chunk (one month).

   Args:
   - db: DataScript DB
   - year-month: String (YYYY-MM format)

   Returns: JournalChunk record"
  [db year-month]
  ;; TODO: Implement in Phase 4
  (throw (js/Error. "Not implemented: writer/extract-journal-chunk")))

(defn extract-metadata-chunk
  "Extract metadata for the graph.

   Args:
   - db: DataScript DB

   Returns: MetadataChunk record"
  [db]
  ;; TODO: Implement in Phase 4
  (throw (js/Error. "Not implemented: writer/extract-metadata-chunk")))

(defn extract-config-chunk
  "Extract configuration chunk.

   Args:
   - db: DataScript DB

   Returns: ConfigChunk record"
  [db]
  ;; TODO: Implement in Phase 4
  (throw (js/Error. "Not implemented: writer/extract-config-chunk")))

(defn save-chunk
  "Serialize, compress, and save a single chunk.

   Args:
   - graph-name: String
   - chunk-key: String (e.g., 'pages/programming')
   - chunk: Chunk record
   - options: {:compression-level 3}

   Returns: Promise<ChunkMetadata>

   Steps:
   1. Serialize chunk to Transit MessagePack
   2. Compress with zstd
   3. Compute SHA-256 hash
   4. Save atomically (.tmp → rename)
   5. Return metadata"
  [graph-name chunk-key chunk & [options]]
  ;; TODO: Implement in Phase 4
  (p/rejected (js/Error. "Not implemented: writer/save-chunk")))

(defn save-incremental
  "Save only changed chunks after a transaction.

   Args:
   - repo: String
   - graph-name: String
   - db: DataScript DB (db-after)
   - changed-chunks: {:pages #{String} :journals #{String}}

   Returns: Promise<void>

   Steps:
   1. Extract changed chunks from DB
   2. Serialize and compress each chunk
   3. Save chunks in parallel
   4. Update manifest with new metadata
   5. Save manifest"
  [repo graph-name db changed-chunks]
  ;; TODO: Implement in Phase 4
  (p/rejected (js/Error. "Not implemented: writer/save-incremental")))

(defn migrate-full-graph
  "Migrate entire graph from monolithic to chunked format.

   Args:
   - repo: String
   - graph-name: String
   - db: DataScript DB

   Returns: Promise<void>

   Steps:
   1. Extract all chunks from DB
   2. Serialize and compress all chunks
   3. Save all chunks
   4. Create and save manifest
   5. Keep old monolithic file as backup"
  [repo graph-name db]
  ;; TODO: Implement in Phase 4
  (p/rejected (js/Error. "Not implemented: writer/migrate-full-graph")))

;; =============================================================================
;; Helper Functions (to be implemented)
;; =============================================================================

(defn journal-day->year-month
  "Convert journal day integer to year-month string.

   Args:
   - journal-day: Long (e.g., 20251203 for Dec 3, 2025)

   Returns: String (e.g., '2025-12')"
  [journal-day]
  ;; TODO: Implement in Phase 4
  (let [day-str (str journal-day)
        year (subs day-str 0 4)
        month (subs day-str 4 6)]
    (str year "-" month)))

(defn parse-year-month
  "Parse year-month string to date range.

   Args:
   - year-month: String (YYYY-MM)
   - boundary: :start | :end

   Returns: Long (journal day integer)"
  [year-month boundary]
  ;; TODO: Implement in Phase 4
  (let [[year month] (clojure.string/split year-month #"-")
        year-int (js/parseInt year 10)
        month-int (js/parseInt month 10)]
    (case boundary
      :start (+ (* year-int 10000) (* month-int 100) 1)
      :end (+ (* year-int 10000) (* month-int 100) 31))))  ; Max days approximation
