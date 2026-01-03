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
  (:require [promesa.core :as p]))

;; =============================================================================
;; Interface Definitions (to be implemented)
;; =============================================================================

(defn exists?
  "Check if a manifest exists for a graph.

   Args:
   - graph-name: String

   Returns: Promise<Boolean>"
  [graph-name]
  ;; TODO: Implement in Phase 2
  (p/rejected (js/Error. "Not implemented: manifest/exists?")))

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
  ;; TODO: Implement in Phase 2
  (p/rejected (js/Error. "Not implemented: manifest/read")))

(defn create
  "Create a new manifest for a graph.

   Args:
   - graph-name: String
   - options: {:serialization :msgpack
               :compression :zstd
               :compression-level 3
               :transit-caching true}

   Returns: Promise<Manifest record>"
  [graph-name options]
  ;; TODO: Implement in Phase 2
  (p/rejected (js/Error. "Not implemented: manifest/create")))

(defn update
  "Update manifest with new/changed chunks.

   Args:
   - graph-name: String
   - manifest: Manifest record
   - changes: {:pages #{'programming' 'ideas'}
               :journals #{'2025-12'}}

   Returns: Promise<Manifest record>"
  [graph-name manifest changes]
  ;; TODO: Implement in Phase 2
  (p/rejected (js/Error. "Not implemented: manifest/update")))

(defn add-chunk
  "Add or update a chunk's metadata in the manifest.

   Args:
   - manifest: Manifest record
   - chunk-type: :metadata | :config | :journal | :page
   - chunk-key: String (e.g., '2025-12' for journal, 'programming' for page)
   - chunk-metadata: ChunkMetadata record

   Returns: Manifest record (updated)"
  [manifest chunk-type chunk-key chunk-metadata]
  ;; TODO: Implement in Phase 2
  (throw (js/Error. "Not implemented: manifest/add-chunk")))

(defn get-chunk-metadata
  "Get metadata for a specific chunk.

   Args:
   - manifest: Manifest record
   - chunk-type: :metadata | :config | :journal | :page
   - chunk-key: String (optional for :metadata and :config)

   Returns: ChunkMetadata record or nil"
  [manifest chunk-type & [chunk-key]]
  ;; TODO: Implement in Phase 2
  nil)

(defn compute-stats
  "Compute storage statistics from manifest.

   Returns: Promise<{:total-size Long
                     :compressed-size Long
                     :compression-ratio Float
                     :chunk-count Long
                     :largest-chunk ChunkMetadata}>"
  [manifest]
  ;; TODO: Implement in Phase 2
  (p/rejected (js/Error. "Not implemented: manifest/compute-stats")))

(defn save
  "Serialize, compress, and save manifest.

   Args:
   - graph-name: String
   - manifest: Manifest record

   Returns: Promise<void>"
  [graph-name manifest]
  ;; TODO: Implement in Phase 2
  (p/rejected (js/Error. "Not implemented: manifest/save")))
