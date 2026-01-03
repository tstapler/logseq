(ns frontend.db.chunked-test
  "Tests for chunked database data structures"
  (:require [cljs.test :refer [use-fixtures deftest is are testing]]
            [frontend.db.chunked :as chunked]
            [frontend.test.helper :as test-helper]))

(use-fixtures :each {:before test-helper/start-test-db!
                     :after test-helper/destroy-test-db!})

(deftest test-storage-format-detection
  (testing "Should detect non-chunked format"
    (is (= :chunked (chunked/detect-storage-format nil {})))
    (is (= :chunked (chunked/detect-storage-format {} {})))
    (is (= :chunked (chunked/detect-storage-format {} {:format "chunked"})))
    (is (= :monolithic (chunked/detect-storage-format {} {:format "monolithic"}))))

  (testing "Should detect chunked format from manifest"
    (let [manifest {:format "chunked" :version 1}]
      (is (= :chunked (chunked/detect-storage-format manifest {})))
      (is (= :chunked (chunked/detect-storage-format manifest nil))))))

(deftest test-manifest-creation
  (testing "Should create valid manifest"
    (let [manifest (chunked/create-manifest)]
      (are [k v] (= v (get manifest k))
           :format "chunked"
           :version 1
           :created-at inst?
           :updated-at inst?
           :pages {}
           :journal {}
           :metadata {}
           :config {}
           :chunk-stats {}
           :compression "zstd"
           :serialization "transit+msgpack"))))

(deftest test-chunk-metadata-creation
  (testing "Should create chunk metadata"
    (let [metadata (chunked/create-chunk-metadata)]
      (are [k v] (= v (get metadata k))
           :chunk-id string?
           :format "zstd+transit"
           :compression "zstd"
           :serialization "transit+msgpack"
           :size-raw 0
           :size-compressed 0
           :created-at inst?
           :updated-at inst?
           :checksum ""
           :version 1))))

(deftest test-page-chunk-creation
  (testing "Should create page chunk"
    (let [pages {"test-page" {:block/uuid #uuid"550e8400-e29b-41d4-a716-446655440000"
                               :block/content "test content"}}
          chunk (chunked/create-page-chunk pages)]
      (are [k v] (= v (get chunk k))
           :chunk-type :page
           :pages pages
           :metadata (chunked/create-chunk-metadata))
      (is (= "page" (get-in chunk [:metadata :chunk-id]))))))

(deftest test-journal-chunk-creation
  (testing "Should create journal chunk"
    (let [journals {"2024-01-01" {:block/uuid #uuid"550e8400-e29b-41d4-a716-446655440001"
                                  :block/content "journal entry"}}
          chunk (chunked/create-journal-chunk journals)]
      (are [k v] (= v (get chunk k))
           :chunk-type :journal
           :journals journals
           :metadata (chunked/create-chunk-metadata))
      (is (= "journal" (get-in chunk [:metadata :chunk-id]))))))

(deftest test-metadata-chunk-creation
  (testing "Should create metadata chunk"
    (let [metadata {"blocks" {"test-id" {:block/uuid #uuid"550e8400-e29b-41d4-a716-446655440002"}}}
          chunk (chunked/create-metadata-chunk metadata)]
      (are [k v] (= v (get chunk k))
           :chunk-type :metadata
           :metadata-data metadata
           :metadata (chunked/create-chunk-metadata))
      (is (= "metadata" (get-in chunk [:metadata :chunk-id]))))))

(deftest test-config-chunk-creation
  (testing "Should create config chunk"
    (let [config {:theme "dark" :language "en"}
          chunk (chunked/create-config-chunk config)]
      (are [k v] (= v (get chunk k))
           :chunk-type :config
           :config-data config
           :metadata (chunked/create-chunk-metadata))
      (is (= "config" (get-in chunk [:metadata :chunk-id]))))))

(deftest test-manifest-stats-computation
  (testing "Should compute manifest statistics"
    (let [chunk1 (assoc (chunked/create-chunk-metadata) :size-raw 1000 :size-compressed 200)
          chunk2 (assoc (chunked/create-chunk-metadata) :size-raw 2000 :size-compressed 300)
          manifest {:pages {"page1" chunk1}
                    :journal {"journal1" chunk2}
                    :metadata {"metadata1" chunk1}}
          stats (chunked/compute-manifest-stats manifest)]
      (are [k v] (= v (get stats k))
           :total-chunks 3
           :total-size-raw 4000
           :total-size-compressed 700
           :compression-ratio 0.175)
      (is (< (:estimated-load-time stats) 100)))))