(ns frontend.db
  "Main entry ns for db related fns"
  (:require [clojure.core.async :as async]
            [datascript.core :as d]
            [logseq.db.schema :as db-schema]
            [frontend.db.conn :as conn]
            [logseq.db.default :as default-db]
            [frontend.db.model]
            [frontend.db.query-custom]
            [frontend.db.query-react]
            [frontend.db.react :as react]
            [frontend.db.utils]
            [frontend.db.persist :as db-persist]
            [frontend.db.migrate :as db-migrate]
            [frontend.db.chunked :as chunked]
            [frontend.db.manifest :as manifest]
            [frontend.db.reader :as reader]
            [frontend.db.writer :as writer]
            [frontend.namespaces :refer [import-vars]]
            [frontend.state :as state]
            [frontend.util :as util]
            [promesa.core :as p]
            [electron.ipc :as ipc]))

(import-vars
 [frontend.db.conn
  ;; TODO: remove later
  conns
  get-repo-path
  get-repo-name
  get-short-repo-name
  datascript-db
  get-db
  remove-conn!]

 [frontend.db.utils
  db->json db->edn-str db->string get-max-tx-id get-tx-id
  group-by-page seq-flatten
  string->db

  entity pull pull-many transact! get-key-value]

 [frontend.db.model
  blocks-count blocks-count-cache delete-blocks get-pre-block
  delete-files delete-pages-by-files get-all-block-contents get-all-block-avets get-all-tagged-pages get-single-block-contents
  get-all-templates get-block-and-children get-block-by-uuid get-block-children sort-by-left
  get-block-parent get-block-parents parents-collapsed? get-block-referenced-blocks get-all-referenced-blocks-uuid
  get-block-children-ids get-block-immediate-children get-block-page
  get-custom-css get-date-scheduled-or-deadlines
  get-file-last-modified-at get-file get-file-page get-file-page-id file-exists?
  get-files get-files-blocks get-files-full get-journals-length get-pages-with-file
  get-latest-journals get-page get-page-alias get-page-alias-names get-paginated-blocks
  get-page-blocks-count get-page-blocks-no-cache get-page-file get-page-format get-page-properties
  get-page-referenced-blocks get-page-referenced-blocks-full get-page-referenced-pages get-page-unlinked-references
  get-all-pages get-pages get-pages-relation get-pages-that-mentioned-page get-tag-pages
  journal-page? page-alias-set sub-block
  set-file-last-modified-at! page-empty? page-exists? page-empty-or-dummy? get-alias-source-page
  set-file-content! has-children? get-namespace-pages get-all-namespace-relation get-pages-by-name-partition
  get-original-name]

 [frontend.db.react
  get-current-page set-key-value
  remove-key! remove-q! remove-query-component! add-q! add-query-component! clear-query-state!
  clear-query-state-without-refs-and-embeds! kv q
  query-state query-components remove-custom-query! set-new-result! sub-key-value refresh!]

 [frontend.db.query-custom
  custom-query]

 [frontend.db.query-react
  react-query custom-query-result-transform]

 [frontend.db.chunked
  detect-storage-format create-manifest restore-chunked! persist-chunked-incremental! migrate-to-chunked!
  compute-manifest-stats]

 [frontend.db.manifest
  create-manifest register-chunk remove-chunk compute-stats valid?]

 [frontend.db.reader
  create-reader get-cached cache-chunk! clear-cache! get-progress load-chunk
  load-phase-1 load-phase-2 load-phase-3]

 [frontend.db.writer
  create-writer detect-changes! create-chunks add-change! clear-changes!
  prepare-incremental-save prepare-full-migration get-stats]

 [logseq.db.default built-in-pages-names built-in-pages])

;; Chunked database integration functions

(defn- get-chunked-db-name
  "Get chunked database name for a repo"
  [repo]
  (str repo "-chunked"))

(defn should-use-chunked-storage?
  "Check if chunked storage should be used for this repo"
  [repo]
  (let [manifest (manifest/load-manifest (get-chunked-db-name repo))]
    (or manifest
        (state/chunked-storage-enabled? repo)
        ;; Auto-enable for large graphs (>10MB serialized)
        (let [serialized-size (-> (db-persist/get-serialized-graph (datascript-db repo))
                                count)]
          (> serialized-size 10485760)))))

(defn restore-chunked-graph!
  "Restore graph using chunked storage format"
  [repo]
  (p/let [db-name (get-chunked-db-name repo)
          manifest (manifest/load-manifest db-name)]
    (when manifest
      (let [reader-instance (reader/create-reader manifest)]
        ;; Phase 1: Load essential chunks quickly
        (reader/load-phase-1 reader-instance)
        ;; Phase 2: Load additional chunks
        (reader/load-phase-2 reader-instance)
        ;; Phase 3: Load remaining chunks
        (reader/load-phase-3 reader-instance)))))

(defn persist-chunked-graph!
  "Persist graph using chunked storage format"
  [repo]
  (p/let [db-name (get-chunked-db-name repo)
          db (get-db repo)
          manifest (or (manifest/load-manifest db-name) (chunked/create-manifest))
          writer-instance (writer/create-writer manifest)
          current-data (db->json db)]
    
    ;; Detect changes and create chunks
    (let [chunks (writer/create-chunks writer-instance current-data)
          save-data (writer/prepare-incremental-save writer-instance)]
      ;; Save chunks and manifest
      (doseq [[chunk-type chunk-id chunk-data] chunks]
        (chunked/save-chunk! db-name chunk-type chunk-id chunk-data))
      (manifest/save-manifest! db-name (:manifest save-data)))))

(defn migrate-to-chunked-storage!
  "Migrate existing monolithic graph to chunked storage"
  [repo]
  (p/let [db (get-db repo)
          current-data (db->json db)
          manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)
          migration-data (writer/prepare-full-migration writer-instance current-data)
          db-name (get-chunked-db-name repo)]
    
    ;; Save all chunks from migration
    (doseq [[chunk-type chunk-id chunk-data] (:chunks migration-data)]
      (chunked/save-chunk! db-name chunk-type chunk-id chunk-data))
    
    ;; Save manifest
    (manifest/save-manifest! db-name (:manifest migration-data))
    
    ;; Mark migration complete
    (state/set-chunked-storage-enabled! repo true)))

(defn listen-and-persist!
  [repo]
  (when-let [conn (get-db repo false)]
    (d/unlisten! conn :persistence)
    (repo-listen-to-tx! repo conn)))

(defn restore-graph!
  "Restore db from serialized db cache"
  [repo]
  (p/let [db-name (datascript-db repo)
          stored (db-persist/get-serialized-graph db-name)]
    (restore-graph-from-text! repo stored)))

(defn restore-with-chunked-support!
  "Restore graph with automatic chunked storage detection"
  [repo]
  (if (should-use-chunked-storage? repo)
    (p/let [_ (restore-chunked-graph! repo)]
      (listen-and-persist! repo))
    (restore-graph! repo)))

(defn persist-with-chunked-support!
  "Persist graph with automatic chunked storage detection"
  [repo]
  (if (should-use-chunked-storage? repo)
    (persist-chunked-graph! repo)
    (let [key (datascript-db repo)
          db (get-db repo)]
      (when db
        (let [db-str (if db (db->string db) "")]
          (p/let [_ (db-persist/save-graph! key db-str)]))))))

(defn get-storage-stats
  "Get storage statistics for repository"
  [repo]
  (if (should-use-chunked-storage? repo)
    (let [manifest (manifest/load-manifest (get-chunked-db-name repo))]
      (when manifest
        (manifest/compute-stats manifest)))
    {:format :monolithic
     :total-size (-> (db-persist/get-serialized-graph (datascript-db repo))
                     count)}))

(defn enable-chunked-storage!
  "Enable chunked storage for a repository"
  [repo]
  (state/set-chunked-storage-enabled! repo true)
  (migrate-to-chunked-storage! repo))

(defn- old-schema?
  "Requires migration if schema version is older than db-schema/version"
  [db]
  (let [v (db-migrate/get-schema-version db)
        ;; backward compatibility
        v (if (integer? v) v 0)]
    (cond
      (= db-schema/version v)
      false

      (< db-schema/version v)
      (do
        (js/console.error "DB schema version is newer than app, please update app. " ":db-version" v)
        false)

      :else
      true)))

(defonce persistent-jobs (atom {}))

(defn clear-repo-persistent-job!
  [repo]
  (when-let [old-job (get @persistent-jobs repo)]
    (js/clearTimeout old-job)))

(defn persist-if-idle!
  [repo]
  (clear-repo-persistent-job! repo)
  (let [job (js/setTimeout
             (fn []
               (if (and (state/input-idle? repo)
                        (state/db-idle? repo)
                        ;; It's ok to not persist here since new changes
                        ;; will be notified when restarting the app.
                        (not (state/whiteboard-route?)))
                (persist-with-chunked-support! repo)
                ;; (state/set-db-persisted! repo true)

                (persist-if-idle! repo)))
             3000)]
    (swap! persistent-jobs assoc repo job)))

(defonce *db-listener (atom nil))

(defn- repo-listen-to-tx!
  [repo conn]
  (d/listen! conn :persistence
             (fn [tx-report]
               (when (not (:new-graph? (:tx-meta tx-report))) ; skip initial txs
                 (if (util/electron?)
                   (when-not (:dbsync? (:tx-meta tx-report))
                     ;; sync with other windows if needed
                     (p/let [graph-has-other-window? (ipc/ipc "graphHasOtherWindow" repo)]
                       (when graph-has-other-window?
                         (ipc/ipc "dbsync" repo {:data (db->string (:tx-data tx-report))}))))
                   (do
                     (state/set-last-transact-time! repo (util/time-ms))
                     (persist-if-idle! repo)))

                 (when-let [db-listener @*db-listener]
                   (db-listener repo tx-report))))))



(defn restore-graph!
  "Restore db from serialized db cache"
  [repo]
  (p/let [db-name (datascript-db repo)
          stored (db-persist/get-serialized-graph db-name)]
    (restore-graph-from-text! repo stored)))

(defn restore!
  [repo]
  (restore-with-chunked-support! repo))

(defn run-batch-txs!
  []
  (let [chan (state/get-db-batch-txs-chan)]
    (async/go-loop []
      (let [f (async/<! chan)]
        (f))
      (recur))
    chan))

(defn new-block-id
  []
  (d/squuid))