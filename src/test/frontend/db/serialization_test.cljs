(ns frontend.db.serialization-test
  "Tests for chunked database serialization"
  (:require [cljs.test :refer [use-fixtures deftest is are testing]]
            [frontend.db.serialization :as serialization]
            [frontend.test.helper :as test-helper]))

(use-fixtures :each {:before test-helper/start-test-db!
                     :after test-helper/destroy-test-db!})

(deftest test-serialization-interface
  (testing "Should have serialization interface available"
    (is (fn? serialization/serialize) "Serialize function should exist")
    (is (fn? serialization/deserialize) "Deserialize function should exist")))

(deftest test-round-trip-serialization
  (testing "Should serialize and deserialize complex data correctly"
    (let [test-data {:pages {"test-page" {:block/uuid #uuid"550e8400-e29b-41d4-a716-446655440000"
                                          :block/content "Test content with special chars: ñáéíóú"
                                          :block/format :markdown}}
                     :metadata {:version 1 :created-at #inst "2024-01-01T00:00:00Z"}
                     :nested {:deeply {:nested {:data [1 2 3]}}}}]
      (let [serialized (serialization/serialize test-data)
            deserialized (serialization/deserialize serialized)]
        (is (= test-data deserialized) "Data should round-trip correctly")))))

(deftest test-empty-data-serialization
  (testing "Should handle empty data"
    (let [empty-data {}
          serialized (serialization/serialize empty-data)
          deserialized (serialization/deserialize serialized)]
      (is (= empty-data deserialized) "Empty data should round-trip correctly"))))

(deftest test-special-values-serialization
  (testing "Should handle special values"
    (let [special-data {:nil-value nil
                        :empty-string ""
                        :number 42
                        :float 3.14159
                        :boolean true
                        :keywords [:test :data]
                        :set #{:a :b :c}
                        :vector [1 2 3]
                        :map {:nested "value"}}
          serialized (serialization/serialize special-data)
          deserialized (serialization/deserialize serialized)]
      (is (= special-data deserialized) "Special values should round-trip correctly"))))

(deftest test-large-data-serialization
  (testing "Should handle large data structures"
    (let [large-data {:pages (into {} (for [i (range 100)]
                                         [(str "page-" i) {:content (apply str (repeat 100 "content "))}]))
                       :blocks (into [] (for [i (range 1000)]
                                           {:id i :data (str "block-" i)}))}
          serialized (serialization/serialize large-data)
          deserialized (serialization/deserialize serialized)]
      (is (= large-data deserialized) "Large data should round-trip correctly"))))

(deftest test-unicode-serialization
  (testing "Should handle Unicode characters"
    (let [unicode-data {:unicode "Test with Unicode: 🚀 ✓ × ñáéíóú 中文 русский العربية"
                        :emoji ["😀" "😎" "🎉" "🚀"]
                        :international {:en "English" :es "Español" :zh "中文" :ru "русский"}}]
      (let [serialized (serialization/serialize unicode-data)
            deserialized (serialization/deserialize serialized)]
        (is (= unicode-data deserialized) "Unicode data should round-trip correctly")))))