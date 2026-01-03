(ns frontend.db.chunked.reader
  "Chunk reading and progressive loading for chunked database storage.

   Implements 3-phase progressive loading:
   - Phase 1 (Critical <500ms): manifest + metadata + config + current month + recent pages
   - Phase 2 (Deferred): previous months + favorite pages
   - Phase 3 (On-demand): historical journals + linked pages

   Uses LRU cache for recently accessed pages."
  (:require [promesa.core :as p]
            [datascript.core :as d]
            [frontend.db.chunked.compress :as compress]
            [frontend.db.chunked.serialization :as ser]
            [frontend.db.chunked.manifest :as manifest]
            [frontend.db.persist :as persist]
            [lambdaisland.glogi :as log]))

;; =============================================================================
;; LRU Cache Implementation
;; =============================================================================

(defn create-lru-cache
  "Create an LRU cache for recently accessed pages.

   Args:
   - size: Long (maximum number of cached pages)

   Returns: LRU cache instance (atom with map and access-order list)"
  [size]
  (atom {:max-size size
         :cache {}          ;; chunk-key → chunk data
         :access-order []}));; [chunk-key ...] in LRU order

(defn cache-get
  "Get a chunk from LRU cache.

   Args:
   - cache: LRU cache instance (atom)
   - chunk-key: String

   Returns: Chunk record or nil"
  [cache chunk-key]
  (when-let [chunk (get-in @cache [:cache chunk-key])]
    ;; Update access order (move to end = most recent)
    (swap! cache (fn [state]
                   (update state :access-order
                           (fn [order]
                             (conj (vec (remove #{chunk-key} order)) chunk-key)))))
    chunk))

(defn cache-put
  "Put a chunk in LRU cache.

   Args:
   - cache: LRU cache instance (atom)
   - chunk-key: String
   - chunk: Chunk record

   Returns: cache (updated)"
  [cache chunk-key chunk]
  (swap! cache (fn [state]
                 (let [current-size (count (:access-order state))
                       max-size (:max-size state)
                       ;; If at capacity, remove LRU item (first in order)
                       state' (if (and (>= current-size max-size)
                                       (not (contains? (:cache state) chunk-key)))
                                (let [lru-key (first (:access-order state))]
                                  (-> state
                                      (update :cache dissoc lru-key)
                                      (update :access-order #(vec (rest %)))))
                                state)]
                   ;; Add/update chunk and access order
                   (-> state'
                       (assoc-in [:cache chunk-key] chunk)
                       (update :access-order
                               (fn [order]
                                 (conj (vec (remove #{chunk-key} order)) chunk-key)))))))
  cache)

;; =============================================================================
;; Chunk Reading
;; =============================================================================

(defn- verify-chunk-hash
  "Verify chunk hash matches expected value.

   Args:
   - data: Uint8Array (chunk data)
   - expected-hash: String

   Returns: Boolean"
  [data expected-hash]
  ;; TODO: Implement SHA-256 hash verification
  ;; For now, skip verification (will add in Phase 4)
  true)

(defn read-chunk
  "Read and deserialize a single chunk.

   Args:
   - graph-name: String
   - chunk-key: String (e.g., 'pages/programming')
   - chunk-type: :metadata | :config | :journal | :page
   - chunk-metadata: ChunkMetadata record

   Returns: Promise<Chunk record>

   Steps:
   1. Read compressed bytes from storage
   2. Decompress with zstd
   3. Deserialize from Transit MessagePack
   4. Verify hash
   5. Return chunk record"
  [graph-name chunk-key chunk-type chunk-metadata]
  (p/let [_ (log/debug :reader/read-chunk (str "Reading chunk: " chunk-key))

          ;; Read from storage
          compressed (persist/get-chunk graph-name chunk-key)]

    (if-not compressed
      (p/rejected (js/Error. (str "Chunk not found: " chunk-key)))

      (p/let [;; Decompress
              decompressed (compress/decompress compressed)

              ;; Verify hash (optional, can be expensive)
              _ (when (:hash chunk-metadata)
                  (when-not (verify-chunk-hash decompressed (:hash chunk-metadata))
                    (log/warn :reader/read-chunk "Hash mismatch for chunk:" chunk-key)))

              ;; Deserialize
              chunk-data (ser/deserialize-chunk decompressed chunk-type)]

        (log/debug :reader/read-chunk
                   (str "Chunk loaded: " chunk-key)
                   {:size (:size chunk-metadata)
                    :compressed-size (:compressed-size chunk-metadata)})
        chunk-data))))

(defn read-chunk-batch
  "Read multiple chunks in parallel.

   Args:
   - graph-name: String
   - chunk-specs: [{:type :page :key 'programming' :metadata ChunkMetadata}]

   Returns: Promise<[Chunk records]>"
  [graph-name chunk-specs]
  (let [chunk-promises (map (fn [{:keys [type key metadata]}]
                              (read-chunk graph-name key type metadata))
                            chunk-specs)]
    (p/all chunk-promises)))

;; =============================================================================
;; Progressive Loading Helpers
;; =============================================================================

(defn- get-current-year-month
  "Get current year-month string (YYYY-MM).

   Returns: String"
  []
  (let [now (js/Date.)
        year (.getFullYear now)
        month (inc (.getMonth now))]
    (str year "-" (if (< month 10) (str "0" month) month))))

(defn- get-previous-months
  "Get previous N month strings.

   Args:
   - count: Long (number of previous months)

   Returns: [String] (YYYY-MM strings)"
  [count]
  (let [now (js/Date.)
        months (atom [])]
    (dotimes [i count]
      (let [date (js/Date. (.getTime now))
            _ (.setMonth date (- (.getMonth now) (inc i)))
            year (.getFullYear date)
            month (inc (.getMonth date))]
        (swap! months conj (str year "-" (if (< month 10) (str "0" month) month)))))
    @months))

(defn get-critical-chunks
  "Identify critical chunks for Phase 1 loading.

   Args:
   - manifest: Manifest record
   - options: {:recent-pages-count 5
               :current-month-only true}

   Returns: [{:type :metadata | :config | :journal | :page
              :key String
              :metadata ChunkMetadata}]"
  [manifest & [{:keys [recent-pages-count current-month-only]
                :or {recent-pages-count 5
                     current-month-only true}}]]
  (let [critical-chunks (atom [])]

    ;; Always load metadata and config
    (when-let [metadata-chunk (manifest/get-chunk-metadata manifest :metadata)]
      (swap! critical-chunks conj {:type :metadata
                                   :key "metadata"
                                   :metadata metadata-chunk}))

    (when-let [config-chunk (manifest/get-chunk-metadata manifest :config)]
      (swap! critical-chunks conj {:type :config
                                   :key "config"
                                   :metadata config-chunk}))

    ;; Load current month's journal
    (let [current-month (get-current-year-month)]
      (when-let [journal-chunk (manifest/get-chunk-metadata manifest :journal current-month)]
        (swap! critical-chunks conj {:type :journal
                                     :key current-month
                                     :metadata journal-chunk})))

    ;; Load most recently updated pages (LRU strategy)
    (let [page-chunks (get-in manifest [:chunks :pages] {})
          recent-pages (take recent-pages-count
                             (sort-by (comp :updated-at second) > page-chunks))]
      (doseq [[page-name page-metadata] recent-pages]
        (swap! critical-chunks conj {:type :page
                                     :key page-name
                                     :metadata page-metadata})))

    @critical-chunks))

(defn get-deferred-chunks
  "Identify deferred chunks for Phase 2 loading.

   Args:
   - manifest: Manifest record
   - options: {:favorite-pages [String]
               :previous-months-count 2}

   Returns: [{:type :journal | :page
              :key String
              :metadata ChunkMetadata}]"
  [manifest & [{:keys [favorite-pages previous-months-count]
                :or {favorite-pages []
                     previous-months-count 2}}]]
  (let [deferred-chunks (atom [])]

    ;; Load previous months' journals
    (let [prev-months (get-previous-months previous-months-count)]
      (doseq [year-month prev-months]
        (when-let [journal-chunk (manifest/get-chunk-metadata manifest :journal year-month)]
          (swap! deferred-chunks conj {:type :journal
                                       :key year-month
                                       :metadata journal-chunk}))))

    ;; Load favorite pages
    (doseq [page-name favorite-pages]
      (when-let [page-chunk (manifest/get-chunk-metadata manifest :page page-name)]
        (swap! deferred-chunks conj {:type :page
                                     :key page-name
                                     :metadata page-chunk})))

    @deferred-chunks))

;; =============================================================================
;; Progressive Loading Implementation
;; =============================================================================

(defn- merge-chunks-into-db
  "Merge chunk data into DataScript database.

   Args:
   - conn: DataScript connection
   - chunks: [Chunk records]

   Returns: DataScript DB (updated)"
  [conn chunks]
  (doseq [chunk chunks]
    (let [;; Extract entities from chunk based on type
          entities (cond
                     ;; Page chunk
                     (:page-entity chunk)
                     (concat [(:page-entity chunk)] (:blocks chunk))

                     ;; Journal chunk
                     (:journals chunk)
                     (concat (:journals chunk) (:blocks chunk))

                     ;; Metadata chunk
                     (:schema-version chunk)
                     []  ;; Metadata doesn't add entities

                     ;; Config chunk
                     (:built-in-pages chunk)
                     (:built-in-pages chunk)

                     :else
                     [])]
      (when (seq entities)
        (d/transact! conn entities))))
  @conn)

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
  (log/info :reader/restore-progressive "Starting progressive load for graph:" graph-name)

  (p/let [;; Create DataScript connection
          ;; TODO: Get schema from metadata chunk or use default
          conn (d/create-conn {})

          ;; === PHASE 1: Critical (<500ms) ===
          _ (log/info :reader/restore-progressive "Phase 1: Loading critical chunks")
          critical-chunk-specs (get-critical-chunks manifest)
          critical-chunks (read-chunk-batch graph-name critical-chunk-specs)

          ;; Merge critical chunks into DB
          _ (merge-chunks-into-db conn critical-chunks)

          _ (log/info :reader/restore-progressive
                      "Phase 1 complete - UI interactive"
                      {:chunks-loaded (count critical-chunks)})

          ;; Return DB immediately for UI interactivity
          db @conn]

    ;; === PHASE 2: Deferred (background, non-blocking) ===
    (p/let [_ (log/info :reader/restore-progressive "Phase 2: Loading deferred chunks (background)")
            deferred-chunk-specs (get-deferred-chunks manifest)
            deferred-chunks (read-chunk-batch graph-name deferred-chunk-specs)

            ;; Merge deferred chunks
            _ (merge-chunks-into-db conn deferred-chunks)

            _ (log/info :reader/restore-progressive
                        "Phase 2 complete"
                        {:chunks-loaded (count deferred-chunks)})]

      ;; Phase 3 (on-demand) will be triggered by user navigation
      ;; For now, return the DB with critical + deferred data loaded
      db)))

;; =============================================================================
;; Validation
;; =============================================================================

(defn validate-all-chunks
  "Validate all chunks for integrity.

   Args:
   - graph-name: String
   - manifest: Manifest record

   Returns: Promise<{:valid? Boolean
                     :errors [String]
                     :checked-chunks Long}>"
  [graph-name manifest]
  (log/info :reader/validate-all-chunks "Validating all chunks for graph:" graph-name)

  (p/let [all-chunk-specs (concat
                           (get-critical-chunks manifest {:recent-pages-count 999999})
                           (get-deferred-chunks manifest {:previous-months-count 999999}))

          errors (atom [])
          checked-count (atom 0)

          ;; Validate each chunk
          _ (p/all
             (map (fn [{:keys [type key metadata]}]
                    (p/catch
                      (p/let [_ (read-chunk graph-name key type metadata)]
                        (swap! checked-count inc)
                        nil)
                      (fn [e]
                        (swap! errors conj (str "Chunk validation failed: " key " - " (.-message e)))
                        nil)))
                  all-chunk-specs))]

    {:valid? (empty? @errors)
     :errors @errors
     :checked-chunks @checked-count}))
