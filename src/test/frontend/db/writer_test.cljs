(ns frontend.db.writer-test
  "Tests for chunked database writer"
  (:require [cljs.test :refer [use-fixtures deftest is are testing]]
            [frontend.db.writer :as writer]
            [frontend.db.chunked :as chunked]
            [frontend.test.helper :as test-helper]))

(use-fixtures :each {:before test-helper/start-test-db!
                     :after test-helper/destroy-test-db!})

(deftest test-writer-creation
  (testing "Should create writer with manifest"
    (let [manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)]
      (is (some? writer-instance))
      (is (= manifest (:manifest writer-instance)))
      (is (map? (:changes writer-instance)))
      (is (zero? (count (:changes writer-instance)))))))

(deftest test-change-detection
  (testing "Should detect changes"
    (let [manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)
          old-data {:pages {"page1" {:content "old"}}}
          new-data {:pages {"page1" {:content "new"}}}]
      (writer/detect-changes! writer-instance old-data new-data)
      (is (pos? (count (:changes writer-instance)))))))

(deftest test-no-change-detection
  (testing "Should not detect changes when data is identical"
    (let [manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)
          data {:pages {"page1" {:content "same"}}}]
      (writer/detect-changes! writer-instance data data)
      (is (zero? (count (:changes writer-instance)))))))

(deftest test-chunk-creation
  (testing "Should create chunks from changes"
    (let [manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)
          changes {:pages {"page1" {:content "new content"}}}]
      (let [chunks (writer/create-chunks writer-instance changes)]
        (is (map? chunks))
        (is (contains? chunks :pages))))))

(deftest test-change-tracking
  (testing "Should track changes correctly"
    (let [manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)]
      (writer/add-change! writer-instance :pages "page1" {:content "new"})
      (writer/add-change! writer-instance :journal "2024-01-01" {:content "journal"})
      (is (= 2 (count (:changes writer-instance))))
      (is (contains? (get-in writer-instance [:changes :pages]) "page1"))
      (is (contains? (get-in writer-instance [:changes :journal]) "2024-01-01")))))

(deftest test-change-clearing
  (testing "Should clear changes after save"
    (let [manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)]
      (writer/add-change! writer-instance :pages "page1" {:content "new"})
      (writer/clear-changes! writer-instance)
      (is (zero? (count (:changes writer-instance)))))))

(deftest test-incremental-save-preparation
  (testing "Should prepare incremental save data"
    (let [manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)]
      (writer/add-change! writer-instance :pages "page1" {:content "new"})
      (let [save-data (writer/prepare-incremental-save writer-instance)]
        (is (contains? save-data :chunks))
        (is (contains? save-data :manifest))))))

(deftest test-full-migration-preparation
  (testing "Should prepare full migration data"
    (let [manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)
          full-data {:pages {"page1" {:content "content1"} "page2" {:content "content2"}}
                     :journal {"2024-01-01" {:content "journal"}}}]
      (let [migration-data (writer/prepare-full-migration writer-instance full-data)]
        (is (contains? migration-data :chunks))
        (is (contains? migration-data :manifest))
        (is (> (count (:chunks migration-data)) 0))))))

(deftest test-writer-stats
  (testing "Should track writer statistics"
    (let [manifest (chunked/create-manifest)
          writer-instance (writer/create-writer manifest)]
      (writer/add-change! writer-instance :pages "page1" {:content "new"})
      (let [stats (writer/get-stats writer-instance)]
        (is (= 1 (:total-changes stats)))
        (is (= 1 (:pages-changed stats)))))))