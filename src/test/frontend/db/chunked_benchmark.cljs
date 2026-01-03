(ns frontend.db.chunked-benchmark
  "Performance benchmarks for chunked database"
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

(defn generate-test-data [page-count block-size]
  "Generate test data with specified page count and block size"
  {:pages (into {} (for [i (range page-count)]
                       [(str "page-" i) 
                        {:block/uuid (random-uuid)
                         :block/content (apply str (repeat block-size "content "))
                         :block/format :markdown}]))
   :journal (into {} (for [i (range 10)]
                        [(str "2024-01-" (inc i))
                         {:block/uuid (random-uuid)
                          :block/content (apply str (repeat 50 "journal entry "))}]))
   :metadata {"version" 1 "created-at" (js/Date.)}
   :config {:theme "dark" :language "en"}})

(defn measure-time [f]
  "Measure execution time of function f in milliseconds"
  (let [start (.now js/Date)]
    (f)
    (- (.now js/Date) start)))

(defn measure-memory-usage [f]
  "Approximate memory usage before and after function"
  (if js/performance.memory
    (let [before (.-usedJSHeapSize js/performance.memory)]
      (f)
      (- (.-usedJSHeapSize js/performance.memory) before))
    0))

(deftest benchmark-serialization-performance
  (testing "Serialization performance benchmarks"
    (let [small-data (generate-test-data 10 100)
          medium-data (generate-test-data 100 500)
          large-data (generate-test-data 1000 200)]
      
      ;; Small data serialization
      (let [serialization-time (measure-time #(serialization/serialize small-data))]
        (is (< serialization-time 50) "Small data should serialize in < 50ms")
        (println "Small data serialization:" serialization-time "ms"))
      
      ;; Medium data serialization
      (let [serialization-time (measure-time #(serialization/serialize medium-data))]
        (is (< serialization-time 200) "Medium data should serialize in < 200ms")
        (println "Medium data serialization:" serialization-time "ms"))
      
      ;; Large data serialization
      (let [serialization-time (measure-time #(serialization/serialize large-data))]
        (is (< serialization-time 1000) "Large data should serialize in < 1000ms")
        (println "Large data serialization:" serialization-time "ms")))))

(deftest benchmark-compression-performance
  (testing "Compression performance benchmarks"
    (let [test-data (generate-test-data 100 500)]
      
      ;; Benchmark different compression levels
      (doseq [level [1 3 5 7]]
        (let [compression-time (measure-time 
                                 #(compress/compress test-data level))]
          (println "Compression level" level ":" compression-time "ms")
          (is (< compression-time 2000) (str "Compression level " level " should complete in < 2s")))))))

(deftest benchmark-chunk-creation-performance
  (testing "Chunk creation performance benchmarks"
    (let [small-data (generate-test-data 10 100)
          medium-data (generate-test-data 100 500)
          large-data (generate-test-data 1000 200)]
      
      ;; Small data chunk creation
      (let [manifest (chunked/create-manifest)
            writer-instance (writer/create-writer manifest)
            chunk-time (measure-time #(writer/create-chunks writer-instance small-data))]
        (is (< chunk-time 100) "Small data chunking should complete in < 100ms")
        (println "Small data chunking:" chunk-time "ms"))
      
      ;; Medium data chunk creation
      (let [manifest (chunked/create-manifest)
            writer-instance (writer/create-writer manifest)
            chunk-time (measure-time #(writer/create-chunks writer-instance medium-data))]
        (is (< chunk-time 500) "Medium data chunking should complete in < 500ms")
        (println "Medium data chunking:" chunk-time "ms"))
      
      ;; Large data chunk creation
      (let [manifest (chunked/create-manifest)
            writer-instance (writer/create-writer manifest)
            chunk-time (measure-time #(writer/create-chunks writer-instance large-data))]
        (is (< chunk-time 2000) "Large data chunking should complete in < 2s")
        (println "Large data chunking:" chunk-time "ms")))))

(deftest benchmark-manifest-operations
  (testing "Manifest operations performance benchmarks"
    (let [manifest (chunked/create-manifest)
          chunk-count 100]
      
      ;; Benchmark chunk registration
      (let [chunks (repeatedly chunk-count #(chunked/create-chunk-metadata))
            registration-time (measure-time 
                               (fn []
                                 (doseq [[i chunk] (map-indexed vector chunks)]
                                   (manifest/register-chunk manifest :pages (str "chunk-" i) chunk))))]
        (is (< registration-time 100) (str "Registering " chunk-count " chunks should complete in < 100ms"))
        (println "Chunk registration for" chunk-count "chunks:" registration-time "ms"))
      
      ;; Manifest stats computation
      (let [stats-time (measure-time #(manifest/compute-stats manifest))]
        (is (< stats-time 50) "Stats computation should complete in < 50ms")
        (println "Manifest stats computation:" stats-time "ms")))))

(deftest benchmark-reader-cache-performance
  (testing "Reader cache performance benchmarks"
    (let [manifest (chunked/create-manifest)
          reader-instance (reader/create-reader manifest 50) ; Small cache
          chunk-count 100]
      
      ;; Benchmark cache operations
      (let [cache-time (measure-time 
                        (fn []
                          (doseq [i (range chunk-count)]
                            (reader/cache-chunk! reader-instance :pages (str "chunk-" i) {:data "test"}))))]
        (is (< cache-time 100) (str "Caching " chunk-count " chunks should complete in < 100ms"))
        (println "Cache operations for" chunk-count "chunks:" cache-time "ms"))
      
      ;; Benchmark cache retrieval
      (let [retrieval-time (measure-time 
                            (fn []
                              (doseq [i (range chunk-count)]
                                (reader/get-cached reader-instance :pages (str "chunk-" i)))))]
        (is (< retrieval-time 50) (str "Retrieving " chunk-count " chunks should complete in < 50ms"))
        (println "Cache retrieval for" chunk-count "chunks:" retrieval-time "ms")))))

(deftest benchmark-full-workflow
  (testing "End-to-end workflow performance benchmarks"
    (let [test-data (generate-test-data 50 300)]
      
      ;; Full workflow: manifest -> writer -> chunks -> serialization
      (let [workflow-time (measure-time
                           (fn []
                             (let [manifest (chunked/create-manifest)
                                   writer-instance (writer/create-writer manifest)
                                   chunks (writer/create-chunks writer-instance test-data)
                                   serialized-chunks (map (fn [[_ _ chunk]]
                                                            (serialization/serialize chunk))
                                                          chunks)]
                               (count serialized-chunks))))]
        (is (< workflow-time 1000) "Full workflow should complete in < 1s")
        (println "Full workflow time:" workflow-time "ms")))))

(deftest benchmark-memory-usage
  (testing "Memory usage benchmarks"
    (let [test-data (generate-test-data 100 500)
          manifest (chunked/create-manifest)]
      
      ;; Memory usage for chunk creation
      (let [memory-delta (measure-memory-usage
                          (fn []
                            (let [writer-instance (writer/create-writer manifest)]
                              (writer/create-chunks writer-instance test-data))))]
        (println "Memory delta for chunk creation:" memory-delta "bytes")
        (is (< memory-delta 10485760) "Memory usage should be reasonable (< 10MB)")))))