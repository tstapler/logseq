(ns frontend.db.reader-test
  "Tests for chunked database reader"
  (:require [cljs.test :refer [use-fixtures deftest is are testing]]
            [frontend.db.reader :as reader]
            [frontend.db.chunked :as chunked]
            [frontend.test.helper :as test-helper]))

(use-fixtures :each {:before test-helper/start-test-db!
                     :after test-helper/destroy-test-db!})

(deftest test-reader-creation
  (testing "Should create reader with manifest"
    (let [manifest (chunked/create-manifest)
          reader-instance (reader/create-reader manifest)]
      (is (some? reader-instance))
      (is (= manifest (:manifest reader-instance)))
      (is (map? (:cache reader-instance)))
      (is (zero? (get-in reader-instance [:stats :loaded-chunks]))))))

(deftest test-cache-hit-operations
  (testing "Should handle cache operations"
    (let [manifest (chunked/create-manifest)
          reader-instance (reader/create-reader manifest)]
      (is (nil? (reader/get-cached reader-instance :pages "nonexistent")))
      (reader/cache-chunk! reader-instance :pages "test" {:data "value"})
      (is (= {:data "value"} (reader/get-cached reader-instance :pages "test")))
      (is (= 1 (get-in reader-instance [:cache :pages :size]))))))

(deftest test-lru-cache-eviction
  (testing "Should evict oldest entries when cache is full"
    (let [manifest (chunked/create-manifest)
          reader-instance (reader/create-reader manifest 2)] ; Small cache size
      (reader/cache-chunk! reader-instance :pages "chunk1" {:data "data1"})
      (reader/cache-chunk! reader-instance :pages "chunk2" {:data "data2"})
      (reader/cache-chunk! reader-instance :pages "chunk3" {:data "data3"})
      (is (nil? (reader/get-cached reader-instance :pages "chunk1")))
      (is (= {:data "data2"} (reader/get-cached reader-instance :pages "chunk2")))
      (is (= {:data "data3"} (reader/get-cached reader-instance :pages "chunk3"))))))

(deftest test-chunk-loading-interface
  (testing "Should provide chunk loading interface"
    (let [manifest (chunked/create-manifest)
          reader-instance (reader/create-reader manifest)]
      (is (fn? reader/load-chunk))
      (is (fn? reader/load-phase-1))
      (is (fn? reader/load-phase-2))
      (is (fn? reader/load-phase-3)))))

(deftest test-progress-tracking
  (testing "Should track loading progress"
    (let [manifest (chunked/create-manifest)
          reader-instance (reader/create-reader manifest)]
      (let [progress (reader/get-progress reader-instance)]
        (is (= 0.0 (:overall progress)))
        (is (= 0.0 (:phase1 progress)))
        (is (= 0.0 (:phase2 progress)))
        (is (= 0.0 (:phase3 progress)))))))

(deftest test-stats-updates
  (testing "Should update statistics"
    (let [manifest (chunked/create-manifest)
          reader-instance (reader/create-reader manifest)]
      (reader/update-stats! reader-instance {:loaded-chunks 5})
      (is (= 5 (get-in reader-instance [:stats :loaded-chunks]))))))

(deftest test-cache-clearing
  (testing "Should clear caches"
    (let [manifest (chunked/create-manifest)
          reader-instance (reader/create-reader manifest)]
      (reader/cache-chunk! reader-instance :pages "test" {:data "value"})
      (reader/cache-chunk! reader-instance :journal "journal" {:data "journal"})
      (reader/clear-cache! reader-instance)
      (is (zero? (get-in reader-instance [:cache :pages :size])))
      (is (zero? (get-in reader-instance [:cache :journal :size]))))))