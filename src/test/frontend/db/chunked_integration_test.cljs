(ns frontend.db.chunked-integration-test
  "Integration tests for chunked database flows"
  (:require [cljs.test :refer [use-fixtures deftest is are testing]]
            [frontend.db.chunked :as chunked]
            [frontend.db.manifest :as manifest]
            [frontend.db.reader :as reader]
            [frontend.db.writer :as writer]
            [frontend.db.compress :as compress]
            [frontend.db.serialization :as serialization]
            [frontend.test.helper :as test-helper]))

(use-fixtures :each {:before test-helper/start-test-db!
                     :after test-helper/destroy-test-db!})

(deftest test-full-round-trip
  (testing "Should complete full round-trip of data through chunked system"
    (let [original-data {:pages {"test-page" {:block/uuid #uuid"550e8400-e29b-41d4-a716-446655440000"
                                              :block/content "Test content"
                                              :block/format :markdown}}
                         :journal {"2024-01-01" {:block/uuid #uuid"550e8400-e29b-41d4-a716-446655440001"
                                                 :block/content "Journal entry"}}}
          
          ;; Create manifest
          manifest (chunked/create-manifest)
          
          ;; Create writer and process changes
          writer-instance (writer/create-writer manifest)
          chunks (writer/create-chunks writer-instance original-data)
          
          ;; Update manifest with chunks
          updated-manifest (reduce (fn [acc [chunk-type chunk-id chunk]]
                                      (manifest/register-chunk acc chunk-type chunk-id (:metadata chunk)))
                                    manifest
                                    chunks)]
      
      (is (manifest/valid? updated-manifest))
      (is (> (count chunks) 0)))))

(deftest test-storage-format-detection-flow
  (testing "Should detect storage format correctly in different scenarios"
    ;; Test with no manifest
    (is (= :chunked (chunked/detect-storage-format nil {})))
    
    ;; Test with chunked manifest
    (let [chunked-manifest {:format "chunked" :version 1}]
      (is (= :chunked (chunked/detect-storage-format chunked-manifest {}))))
    
    ;; Test with monolithic manifest
    (let [monolithic-manifest {:format "monolithic"}]
      (is (= :monolithic (chunked/detect-storage-format monolithic-manifest {}))))))

(deftest test-serialization-compression-flow
  (testing "Should serialize and compress chunks correctly"
    (let [test-chunk {:chunk-type :page
                      :pages {"test" {:content "Test content"}}
                      :metadata (chunked/create-chunk-metadata)}
          serialized (serialization/serialize test-chunk)]
      
      (is (some? serialized))
      
      ;; Test that serialized data can be deserialized
      (let [deserialized (serialization/deserialize serialized)]
        (is (= test-chunk deserialized))))))

(deftest test-manifest-lifecycle
  (testing "Should handle manifest lifecycle correctly"
    (let [initial-manifest (chunked/create-manifest)
          chunk-metadata (assoc (chunked/create-chunk-metadata) 
                                :size-raw 1000 
                                :size-compressed 200)
          
          ;; Register chunk
          manifest-with-chunk (manifest/register-chunk initial-manifest :pages "test" chunk-metadata)
          
          ;; Compute stats
          stats (manifest/compute-stats manifest-with-chunk)]
      
      (is (not= (:updated-at initial-manifest) (:updated-at manifest-with-chunk)))
      (is (= chunk-metadata (get-in manifest-with-chunk [:pages "test"])))
      (is (= 1 (:total-chunks stats)))
      (is (= 1000 (:total-size-raw stats)))
      (is (= 200 (:total-size-compressed stats))))))

(deftest test-chunk-type-handling
  (testing "Should handle all chunk types correctly"
    (let [pages-data {"page1" {:content "Page content"}}
          journal-data {"2024-01-01" {:content "Journal entry"}}
          metadata-data {"version" 1}
          config-data {:theme "dark"}
          
          page-chunk (chunked/create-page-chunk pages-data)
          journal-chunk (chunked/create-journal-chunk journal-data)
          metadata-chunk (chunked/create-metadata-chunk metadata-data)
          config-chunk (chunked/create-config-chunk config-data)]
      
      (is (= :page (:chunk-type page-chunk)))
      (is (= :journal (:chunk-type journal-chunk)))
      (is (= :metadata (:chunk-type metadata-chunk)))
      (is (= :config (:chunk-type config-chunk)))
      
      (is (= pages-data (:pages page-chunk)))
      (is (= journal-data (:journals journal-chunk)))
      (is (= metadata-data (:metadata-data metadata-chunk)))
      (is (= config-data (:config-data config-chunk))))))

(deftest test-performance-considerations
  (testing "Should handle performance-related scenarios"
    (let [large-page-data (into {} (for [i (range 100)]
                                      [(str "page-" i) {:content (apply str (repeat 100 "content "))}]))
          page-chunk (chunked/create-page-chunk large-page-data)
          serialized (serialization/serialize page-chunk)]
      
      ;; Should handle large data without issues
      (is (some? serialized))
      (is (> (count serialized) 0))
      
      ;; Should round-trip correctly
      (let [deserialized (serialization/deserialize serialized)]
        (is (= page-chunk deserialized))))))

(deftest test-error-handling-scenarios
  (testing "Should handle error scenarios gracefully"
    ;; Test with nil data
    (let [nil-chunk (chunked/create-page-chunk nil)]
      (is (some? nil-chunk))
      (is (= {} (:pages nil-chunk))))
    
    ;; Test with empty collections
    (let [empty-chunk (chunked/create-journal-chunk {})]
      (is (some? empty-chunk))
      (is (= {} (:journals empty-chunk))))
    
    ;; Test invalid manifest validation
    (is (not (manifest/valid? nil)))
    (is (not (manifest/valid? {})))
    (is (not (manifest/valid? {:format "invalid"})))))