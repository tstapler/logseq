(ns frontend.db.chunked.manifest
  "Manifest management for chunked database storage.

   The manifest is the index of all chunks in a graph, stored as
   'manifest.msgpack.zst'. It must be loaded first to discover
   available chunks.

   Responsibilities:
   - Create/read/update manifest
   - Track chunk metadata (size, hash, timestamp)
   - Provide fast chunk lookup
   - Compute storage statistics"
  (:require [promesa.core :as p]
            [frontend.db.chunked.compress :as compress]
            [frontend.db.chunked.serialization :as ser]
            [frontend.db.persist :as persist]
            [lambdaisland.glogi :as log]))

;; =============================================================================
;; Storage Abstraction Layer
;; =============================================================================

(defn- get-chunk-key
  "Get the storage key for the manifest chunk.

   Args:
   - graph-name: String

   Returns: String (e.g., 'manifest')"
  [graph-name]
  "manifest")

(defn- get-chunk-from-storage
  "Get a chunk from storage (Electron filesystem or IndexedDB).

   Args:
   - graph-name: String
   - chunk-key: String

   Returns: Promise<Uint8Array | nil>

   Note: This abstracts over Electron and browser storage"
  [graph-name chunk-key]
  (p/let [stored (persist/get-chunk graph-name chunk-key)]
    stored))

(defn- save-chunk-to-storage
  "Save a chunk to storage (Electron filesystem or IndexedDB).

   Args:
   - graph-name: String
   - chunk-key: String
   - data: Uint8Array (compressed chunk data)

   Returns: Promise<void>"
  [graph-name chunk-key data]
  (persist/save-chunk graph-name chunk-key data))

;; =============================================================================
;; Manifest Operations
;; =============================================================================

(defn exists?
  "Check if a manifest exists for a graph.

   Args:
   - graph-name: String

   Returns: Promise<Boolean>"
  [graph-name]
  (p/let [chunk-key (get-chunk-key graph-name)]
    (p/catch
      (p/let [stored (get-chunk-from-storage graph-name chunk-key)]
        (boolean stored))
      (fn [_e]
        false))))

(defn read
  "Read and deserialize the manifest for a graph.

   Args:
   - graph-name: String

   Returns: Promise<Manifest record>

   Throws:
   - If manifest doesn't exist
   - If decompression fails
   - If deserialization fails"
  [graph-name]
  (p/let [chunk-key (get-chunk-key graph-name)
          _ (log/info :manifest/read (str "Reading manifest for graph: " graph-name))
          stored (get-chunk-from-storage graph-name chunk-key)]
    (if-not stored
      (p/rejected (js/Error. (str "Manifest not found for graph: " graph-name)))
      (p/let [decompressed (compress/decompress stored)
              manifest-data (ser/deserialize decompressed)]
        (log/info :manifest/read "Manifest loaded successfully"
                  {:version (:version manifest-data)
                   :chunks (count (get-in manifest-data [:chunks :pages] {}))})
        manifest-data))))

(defn create
  "Create a new manifest for a graph.

   Args:
   - graph-name: String
   - options: {:serialization :msgpack
               :compression :zstd
               :compression-level 3
               :transit-caching true}

   Returns: Manifest record"
  [graph-name & [{:keys [serialization compression compression-level transit-caching]
                  :or {serialization :msgpack
                       compression :zstd
                       compression-level 3
                       transit-caching true}}]]
  (let [now (js/Date.)]
    {:version 1
     :format-version "chunked-v1"
     :created-at now
     :last-updated now
     :serialization serialization
     :compression compression
     :compression-level compression-level
     :transit-caching transit-caching
     :chunks {:metadata nil
              :config nil
              :journals {}
              :pages {}}}))

(defn add-chunk
  "Add or update a chunk's metadata in the manifest.

   Args:
   - manifest: Manifest record
   - chunk-type: :metadata | :config | :journal | :page
   - chunk-key: String (e.g., '2025-12' for journal, 'programming' for page)
   - chunk-metadata: ChunkMetadata record

   Returns: Manifest record (updated)"
  [manifest chunk-type chunk-key chunk-metadata]
  (let [updated-manifest (case chunk-type
                           :metadata
                           (assoc-in manifest [:chunks :metadata] chunk-metadata)

                           :config
                           (assoc-in manifest [:chunks :config] chunk-metadata)

                           :journal
                           (assoc-in manifest [:chunks :journals chunk-key] chunk-metadata)

                           :page
                           (assoc-in manifest [:chunks :pages chunk-key] chunk-metadata)

                           ;; Unknown type - log warning and return unchanged
                           (do
                             (log/warn :manifest/add-chunk
                                       (str "Unknown chunk type: " chunk-type))
                             manifest))]
    ;; Update last-updated timestamp
    (assoc updated-manifest :last-updated (js/Date.))))

(defn get-chunk-metadata
  "Get metadata for a specific chunk.

   Args:
   - manifest: Manifest record
   - chunk-type: :metadata | :config | :journal | :page
   - chunk-key: String (optional for :metadata and :config)

   Returns: ChunkMetadata record or nil"
  [manifest chunk-type & [chunk-key]]
  (case chunk-type
    :metadata
    (get-in manifest [:chunks :metadata])

    :config
    (get-in manifest [:chunks :config])

    :journal
    (get-in manifest [:chunks :journals chunk-key])

    :page
    (get-in manifest [:chunks :pages chunk-key])

    ;; Unknown type
    nil))

(defn update
  "Update manifest with new/changed chunks.

   Args:
   - graph-name: String
   - manifest: Manifest record
   - changes: {:pages #{'programming' 'ideas'}
               :journals #{'2025-12'}}

   Returns: Manifest record (updated)

   Note: This updates the last-updated timestamp but doesn't modify
   individual chunk metadata. Use add-chunk to update chunk metadata."
  [graph-name manifest changes]
  (let [updated-manifest (assoc manifest :last-updated (js/Date.))]
    (log/info :manifest/update
              (str "Manifest updated for graph: " graph-name)
              {:changed-pages (count (:pages changes))
               :changed-journals (count (:journals changes))})
    updated-manifest))

(defn compute-stats
  "Compute storage statistics from manifest.

   Args:
   - manifest: Manifest record

   Returns: {:total-size Long
             :compressed-size Long
             :compression-ratio Float
             :chunk-count Long
             :largest-chunk ChunkMetadata}"
  [manifest]
  (let [all-chunks (concat
                    (when-let [meta-chunk (get-in manifest [:chunks :metadata])]
                      [meta-chunk])
                    (when-let [config-chunk (get-in manifest [:chunks :config])]
                      [config-chunk])
                    (vals (get-in manifest [:chunks :journals] {}))
                    (vals (get-in manifest [:chunks :pages] {})))

        total-size (reduce + 0 (map :size all-chunks))
        compressed-size (reduce + 0 (map :compressed-size all-chunks))
        chunk-count (count all-chunks)
        largest-chunk (when (seq all-chunks)
                        (apply max-key :size all-chunks))
        compression-ratio (if (zero? total-size)
                            0.0
                            (/ (double compressed-size) (double total-size)))]
    {:total-size total-size
     :compressed-size compressed-size
     :compression-ratio compression-ratio
     :chunk-count chunk-count
     :largest-chunk largest-chunk}))

(defn save
  "Serialize, compress, and save manifest.

   Args:
   - graph-name: String
   - manifest: Manifest record

   Returns: Promise<void>"
  [graph-name manifest]
  (p/let [chunk-key (get-chunk-key graph-name)
          _ (log/info :manifest/save (str "Saving manifest for graph: " graph-name))

          ;; Serialize to Transit MessagePack
          serialized (ser/serialize manifest {:caching? (:transit-caching manifest)})

          ;; Compress with zstd
          compressed (compress/compress serialized {:level (:compression-level manifest)})

          ;; Save to storage
          _ (save-chunk-to-storage graph-name chunk-key compressed)]
    (log/info :manifest/save "Manifest saved successfully"
              {:serialized-size (.-length serialized)
               :compressed-size (.-length compressed)
               :compression-ratio (compress/get-compression-ratio
                                   (.-length serialized)
                                   (.-length compressed))})
    nil))
