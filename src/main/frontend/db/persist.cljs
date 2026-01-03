(ns frontend.db.persist
  "Handles operations to persisting db to disk or indexedDB"
  (:require [frontend.util :as util]
            [frontend.idb :as idb]
            [frontend.config :as config]
            [electron.ipc :as ipc]
            [frontend.db.conn :as db-conn]
            [promesa.core :as p]))

(defn get-all-graphs
  []
  (if (util/electron?)
    (p/let [result (ipc/ipc "getGraphs")
            result (vec result)
            ;; backward compatibility (release <= 0.5.4)
            result (if (seq result) result (idb/get-nfs-dbs))]
      result)
    (idb/get-nfs-dbs)))

(defn get-serialized-graph
  [graph-name]
  (if (util/electron?)
    (p/let [result (ipc/ipc "getSerializedGraph" graph-name)
            result (if result result
                       (let [graph-name (str config/idb-db-prefix graph-name)]
                         (idb/get-item graph-name)))]
      result)
    (idb/get-item graph-name)))

(defn save-graph!
  [key value]
  (if (util/electron?)
    (do
      (ipc/ipc "saveGraph" key value)
      ;; remove cache before 0.5.5
      (idb/remove-item! key))
    (idb/set-batch! [{:key key :value value}])))

(defn delete-graph!
  [graph]
  (let [key (db-conn/datascript-db graph)]
    (if (util/electron?)
      (do
        (ipc/ipc "deleteGraph" key)
        (idb/remove-item! key))
     (idb/remove-item! key))))

(defn rename-graph!
  [old-repo new-repo]
  (let [old-key (db-conn/datascript-db old-repo)
        new-key (db-conn/datascript-db new-repo)]
    (if (util/electron?)
      (do
        (js/console.error "rename-graph! is not supported in electron")
        (idb/rename-item! old-key new-key))
      (idb/rename-item! old-key new-key))))

;; =============================================================================
;; Chunked Storage Operations (new for chunked database storage)
;; =============================================================================

(defn get-chunk
  "Get a chunk from storage (Electron filesystem or IndexedDB).

   Args:
   - graph-name: String (graph identifier)
   - chunk-key: String (e.g., 'manifest', 'pages/programming', 'journals/2025-12')

   Returns: Promise<Uint8Array | nil> (compressed chunk data)"
  [graph-name chunk-key]
  (if (util/electron?)
    (p/let [result (ipc/ipc "getChunk" graph-name chunk-key)]
      result)
    ;; Browser: use IndexedDB with composite key
    (let [storage-key (str graph-name "/" chunk-key)]
      (idb/get-item storage-key))))

(defn save-chunk
  "Save a chunk to storage (Electron filesystem or IndexedDB).

   Args:
   - graph-name: String (graph identifier)
   - chunk-key: String (e.g., 'manifest', 'pages/programming')
   - data: Uint8Array (compressed chunk data)

   Returns: Promise<void>"
  [graph-name chunk-key data]
  (if (util/electron?)
    (ipc/ipc "saveChunk" graph-name chunk-key data)
    ;; Browser: use IndexedDB with composite key
    (let [storage-key (str graph-name "/" chunk-key)]
      (idb/set-batch! [{:key storage-key :value data}]))))

