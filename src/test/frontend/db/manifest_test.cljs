(ns frontend.db.manifest-test
  "Tests for chunked database manifest management"
  (:require [cljs.test :refer [use-fixtures deftest is are testing]]
            [frontend.db.manifest :as manifest]
            [frontend.db.chunked :as chunked]
            [frontend.test.helper :as test-helper]))

(use-fixtures :each {:before test-helper/start-test-db!
                     :after test-helper/destroy-test-db!})

(deftest test-manifest-creation
  (testing "Should create new manifest"
    (let [new-manifest (manifest/create-manifest)]
      (are [k v] (= v (get new-manifest k))
           :format "chunked"
           :version 1
           :pages {}
           :journal {}
           :metadata {}
           :config {}
           :chunk-stats {})
      (is (inst? (:created-at new-manifest)))
      (is (inst? (:updated-at new-manifest))))))

(deftest test-chunk-metadata-registration
  (testing "Should register chunk metadata"
    (let [base-manifest (manifest/create-manifest)
          chunk-metadata (chunked/create-chunk-metadata)
          updated-manifest (manifest/register-chunk base-manifest :pages "test-chunk" chunk-metadata)]
      (is (= chunk-metadata (get-in updated-manifest [:pages "test-chunk"])))
      (is (not= (:updated-at base-manifest) (:updated-at updated-manifest))))))

(deftest test-chunk-metadata-removal
  (testing "Should remove chunk metadata"
    (let [base-manifest (manifest/create-manifest)
          chunk-metadata (chunked/create-chunk-metadata)
          manifest-with-chunk (manifest/register-chunk base-manifest :pages "test-chunk" chunk-metadata)
          manifest-without-chunk (manifest/remove-chunk manifest-with-chunk :pages "test-chunk")]
      (is (nil? (get-in manifest-without-chunk [:pages "test-chunk"])))
      (is (not= (:updated-at manifest-with-chunk) (:updated-at manifest-without-chunk))))))

(deftest test-stats-computation
  (testing "Should compute statistics correctly"
    (let [base-manifest (manifest/create-manifest)
          chunk1 (assoc (chunked/create-chunk-metadata) :size-raw 1000 :size-compressed 200)
          chunk2 (assoc (chunked/create-chunk-metadata) :size-raw 2000 :size-compressed 300)
          manifest-with-chunks (-> base-manifest
                                   (manifest/register-chunk :pages "page1" chunk1)
                                   (manifest/register-chunk :journal "journal1" chunk2)
                                   (manifest/register-chunk :metadata "metadata1" chunk1))
          stats (manifest/compute-stats manifest-with-chunks)]
      (are [k v] (= v (get stats k))
           :total-chunks 3
           :total-size-raw 4000
           :total-size-compressed 700
           :compression-ratio 0.175)
      (is (< (:estimated-load-time stats) 1000)))))

(deftest test-manifest-validation
  (testing "Should validate manifest structure"
    (let [valid-manifest (manifest/create-manifest)
          invalid-manifest {:format "invalid"}]
      (is (manifest/valid? valid-manifest))
      (is (not (manifest/valid? invalid-manifest))))))

(deftest test-manifest-versioning
  (testing "Should handle version updates"
    (let [base-manifest (manifest/create-manifest)
          updated-manifest (manifest/update-version base-manifest 2)]
      (is (= 2 (:version updated-manifest)))
      (is (not= (:updated-at base-manifest) (:updated-at updated-manifest))))))

(deftest test-chunk-size-tracking
  (testing "Should track chunk sizes"
    (let [base-manifest (manifest/create-manifest)
          small-chunk (assoc (chunked/create-chunk-metadata) :size-raw 100 :size-compressed 20)
          large-chunk (assoc (chunked/create-chunk-metadata) :size-raw 5000 :size-compressed 800)
          updated-manifest (-> base-manifest
                               (manifest/register-chunk :pages "small" small-chunk)
                               (manifest/register-chunk :pages "large" large-chunk))
          stats (manifest/compute-stats updated-manifest)]
      (is (= 2 (:total-chunks stats)))
      (is (= 5100 (:total-size-raw stats)))
      (is (= 820 (:total-size-compressed stats))))))

(deftest test-manifest-merge
  (testing "Should merge manifests correctly"
    (let [manifest1 (manifest/create-manifest)
          chunk1 (assoc (chunked/create-chunk-metadata) :size-raw 1000 :size-compressed 200)
          manifest2 (-> (manifest/create-manifest)
                        (manifest/register-chunk :pages "page1" chunk1))
          merged-manifest (manifest/merge-manifests manifest1 manifest2)]
      (is (= chunk1 (get-in merged-manifest [:pages "page1"])))
      (is (= (:format manifest1) (:format merged-manifest))))))