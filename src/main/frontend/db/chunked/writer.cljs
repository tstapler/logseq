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
            [datascript.core :as d]
            [frontend.db.chunked.compress :as compress]
            [frontend.db.chunked.serialization :as ser]
            [frontend.db.chunked.manifest :as manifest]
            [frontend.db.persist :as persist]
            [lambdaisland.glogi :as log]
            [clojure.string :as str]))

;; =============================================================================
;; Helper Functions
;; =============================================================================

(defn journal-day->year-month
  "Convert journal day integer to year-month string.

   Args:
   - journal-day: Long (e.g., 20251203 for Dec 3, 2025)

   Returns: String (e.g., '2025-12')"
  [journal-day]
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
  (let [[year month] (str/split year-month #"-")
        year-int (js/parseInt year 10)
        month-int (js/parseInt month 10)]
    (case boundary
      :start (+ (* year-int 10000) (* month-int 100) 1)
      :end (+ (* year-int 10000) (* month-int 100) 31))))  ; Max days approximation

(defn- compute-chunk-hash
  "Compute SHA-256 hash of chunk data.

   Args:
   - data: Uint8Array

   Returns: String (hex hash)

   TODO: Implement actual SHA-256 hashing in Phase 4"
  [data]
  ;; Placeholder - return empty string for now
  "")

;; =============================================================================
;; Change Detection
;; =============================================================================

(defn identify-changed-chunks
  "Identify which chunks were affected by a transaction.

   Args:
   - db: DataScript DB (db-after)
   - tx-data: Transaction data from tx-meta

   Returns: {:pages #{String}      ; page names that changed
             :journals #{String}}   ; year-months that changed (YYYY-MM)"
  [db tx-data]
  (let [changed-pages (atom #{})
        changed-journals (atom #{})]

    ;; Analyze each transaction datom
    (doseq [datom tx-data]
      (let [eid (:e datom)
            entity (d/entity db eid)]

        ;; Check if this is a page
        (when-let [page-name (or (:block/name entity)
                                 (get-in entity [:block/page :block/name]))]
          (swap! changed-pages conj page-name))

        ;; Check if this is a journal entry
        (when (:block/journal? entity)
          (when-let [journal-day (:block/journal-day entity)]
            (let [year-month (journal-day->year-month journal-day)]
              (swap! changed-journals conj year-month))))))

    {:pages @changed-pages
     :journals @changed-journals}))

;; =============================================================================
;; Chunk Extraction
;; =============================================================================

(defn extract-page-chunk
  "Extract all data for a page chunk.

   Args:
   - db: DataScript DB
   - page-name: String

   Returns: PageChunk record"
  [db page-name]
  (let [;; Find page entity
        page-eid (d/entid db [:block/name page-name])

        _ (when-not page-eid
            (throw (js/Error. (str "Page not found: " page-name))))

        ;; Pull page entity
        page-entity (d/pull db '[*] page-eid)

        ;; Find all blocks belonging to this page
        blocks (d/q '[:find [(pull ?b [*]) ...]
                      :in $ ?page
                      :where [?b :block/page ?page]]
                    db page-eid)]

    {:page-name page-name
     :page-entity page-entity
     :blocks blocks}))

(defn extract-journal-chunk
  "Extract all data for a journal chunk (one month).

   Args:
   - db: DataScript DB
   - year-month: String (YYYY-MM format)

   Returns: JournalChunk record"
  [db year-month]
  (let [start-day (parse-year-month year-month :start)
        end-day (parse-year-month year-month :end)

        ;; Find all journal pages in this month
        journals (d/q '[:find [(pull ?p [*]) ...]
                        :in $ ?start ?end
                        :where
                        [?p :block/journal? true]
                        [?p :block/journal-day ?day]
                        [(>= ?day ?start)]
                        [(<= ?day ?end)]]
                      db start-day end-day)

        ;; Extract blocks for each journal page
        journal-eids (map :db/id journals)
        blocks (when (seq journal-eids)
                 (d/q '[:find [(pull ?b [*]) ...]
                        :in $ [?page ...]
                        :where [?b :block/page ?page]]
                      db journal-eids))]

    {:year-month year-month
     :date-range [start-day end-day]
     :journals journals
     :blocks blocks}))

(defn extract-metadata-chunk
  "Extract metadata for the graph.

   Args:
   - db: DataScript DB

   Returns: MetadataChunk record"
  [db]
  (let [;; Count total entities
        total-blocks (d/q '[:find (count ?b) .
                            :where [?b :block/uuid]]
                          db)

        total-pages (d/q '[:find (count ?p) .
                           :where [?p :block/name]]
                         db)

        ;; TODO: Get total-files from file system metadata
        total-files 0]

    {:schema-version 1
     :graph-uuid (str (random-uuid))
     :total-pages (or total-pages 0)
     :total-blocks (or total-blocks 0)
     :total-files total-files
     :created-at (js/Date.)
     :statistics {:compression-ratio 0.0
                  :total-size 0
                  :compressed-size 0}}))

(defn extract-config-chunk
  "Extract configuration chunk.

   Args:
   - db: DataScript DB

   Returns: ConfigChunk record"
  [db]
  (let [;; Find built-in pages
        built-in-pages (d/q '[:find [(pull ?p [*]) ...]
                              :where
                              [?p :block/name]
                              ;; TODO: Filter for built-in pages based on naming convention
                              ]
                            db)]

    {:built-in-pages built-in-pages
     :custom-config {}}))

;; =============================================================================
;; Chunk Saving
;; =============================================================================

(defn save-chunk
  "Serialize, compress, and save a single chunk.

   Args:
   - graph-name: String
   - chunk-key: String (e.g., 'pages/programming')
   - chunk-type: :metadata | :config | :journal | :page
   - chunk: Chunk record
   - options: {:compression-level 3}

   Returns: Promise<ChunkMetadata>

   Steps:
   1. Serialize chunk to Transit MessagePack
   2. Compress with zstd
   3. Compute SHA-256 hash
   4. Save atomically (.tmp → rename)
   5. Return metadata"
  [graph-name chunk-key chunk-type chunk & [{:keys [compression-level] :or {compression-level 3}}]]
  (p/let [_ (log/debug :writer/save-chunk (str "Saving chunk: " chunk-key))

          ;; Serialize chunk
          serialized (ser/serialize-chunk chunk {:caching? true})

          ;; Compress with zstd
          compressed (compress/compress serialized {:level compression-level})

          ;; Compute hash
          hash (compute-chunk-hash compressed)

          ;; Save to storage (atomic write)
          _ (persist/save-chunk graph-name chunk-key compressed)]

    (log/debug :writer/save-chunk
               (str "Chunk saved: " chunk-key)
               {:size (.-length serialized)
                :compressed-size (.-length compressed)
                :compression-ratio (compress/get-compression-ratio
                                    (.-length serialized)
                                    (.-length compressed))})

    ;; Return chunk metadata
    {:key chunk-key
     :size (.-length serialized)
     :compressed-size (.-length compressed)
     :hash hash
     :updated-at (js/Date.)}))

;; =============================================================================
;; Incremental Save
;; =============================================================================

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
  (log/info :writer/save-incremental
            (str "Saving incremental changes for graph: " graph-name)
            {:pages (count (:pages changed-chunks))
             :journals (count (:journals changed-chunks))})

  (p/let [;; Load or create manifest
          manifest-exists? (manifest/exists? graph-name)
          manifest (if manifest-exists?
                     (manifest/read graph-name)
                     (manifest/create graph-name))

          ;; Save changed page chunks
          page-metadata-list
          (p/all
           (for [page-name (:pages changed-chunks)]
             (p/let [chunk (extract-page-chunk db page-name)
                     chunk-key (str "pages/" page-name)
                     metadata (save-chunk graph-name chunk-key :page chunk)]
               metadata)))

          ;; Save changed journal chunks
          journal-metadata-list
          (p/all
           (for [year-month (:journals changed-chunks)]
             (p/let [chunk (extract-journal-chunk db year-month)
                     chunk-key (str "journals/" year-month)
                     metadata (save-chunk graph-name chunk-key :journal chunk)]
               metadata)))

          ;; Update manifest with new chunk metadata
          updated-manifest
          (reduce (fn [m metadata]
                    (let [chunk-key (:key metadata)
                          chunk-type (cond
                                       (str/starts-with? chunk-key "pages/") :page
                                       (str/starts-with? chunk-key "journals/") :journal
                                       :else :metadata)
                          chunk-name (last (str/split chunk-key #"/"))]
                      (manifest/add-chunk m chunk-type chunk-name metadata)))
                  manifest
                  (concat page-metadata-list journal-metadata-list))

          ;; Save updated manifest
          _ (manifest/save graph-name updated-manifest)]

    (log/info :writer/save-incremental "Incremental save complete"
              {:pages-saved (count page-metadata-list)
               :journals-saved (count journal-metadata-list)})
    nil))

;; =============================================================================
;; Full Graph Migration
;; =============================================================================

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
  (log/info :writer/migrate-full-graph "Starting migration to chunked format:" graph-name)

  (p/let [;; Create new manifest
          manifest (manifest/create graph-name)

          ;; Extract and save metadata chunk
          metadata-chunk (extract-metadata-chunk db)
          metadata-meta (save-chunk graph-name "metadata" :metadata metadata-chunk)
          manifest' (manifest/add-chunk manifest :metadata "metadata" metadata-meta)

          ;; Extract and save config chunk
          config-chunk (extract-config-chunk db)
          config-meta (save-chunk graph-name "config" :config config-chunk)
          manifest'' (manifest/add-chunk manifest' :config "config" config-meta)

          ;; Extract all pages
          all-pages (d/q '[:find [?name ...]
                           :where [?p :block/name ?name]]
                         db)

          ;; Save all page chunks in parallel
          page-metadata-list
          (p/all
           (for [page-name all-pages]
             (p/let [chunk (extract-page-chunk db page-name)
                     chunk-key (str "pages/" page-name)
                     metadata (save-chunk graph-name chunk-key :page chunk)]
               metadata)))

          ;; Extract all journal months
          journal-days (d/q '[:find [?day ...]
                              :where
                              [?p :block/journal? true]
                              [?p :block/journal-day ?day]]
                            db)
          journal-months (into #{} (map journal-day->year-month journal-days))

          ;; Save all journal chunks in parallel
          journal-metadata-list
          (p/all
           (for [year-month journal-months]
             (p/let [chunk (extract-journal-chunk db year-month)
                     chunk-key (str "journals/" year-month)
                     metadata (save-chunk graph-name chunk-key :journal chunk)]
               metadata)))

          ;; Update manifest with all chunk metadata
          final-manifest
          (reduce (fn [m metadata]
                    (let [chunk-key (:key metadata)
                          chunk-type (cond
                                       (str/starts-with? chunk-key "pages/") :page
                                       (str/starts-with? chunk-key "journals/") :journal
                                       :else :metadata)
                          chunk-name (last (str/split chunk-key #"/"))]
                      (manifest/add-chunk m chunk-type chunk-name metadata)))
                  manifest''
                  (concat page-metadata-list journal-metadata-list))

          ;; Save manifest
          _ (manifest/save graph-name final-manifest)

          ;; Compute final statistics
          stats (manifest/compute-stats final-manifest)]

    (log/info :writer/migrate-full-graph "Migration complete"
              {:total-pages (count page-metadata-list)
               :total-journals (count journal-metadata-list)
               :total-size (:total-size stats)
               :compressed-size (:compressed-size stats)
               :compression-ratio (:compression-ratio stats)})
    nil))
