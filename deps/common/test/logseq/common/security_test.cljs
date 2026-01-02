(ns logseq.common.security-test
  (:require [cljs.test :refer [deftest testing is]]
            [logseq.common.security :as security]))

(deftest test-safe-path?
  (testing "safe-path? validation"
    (is (security/safe-path? "valid/path"))
    (is (security/safe-path? "file.txt"))
    (is (not (security/safe-path? "../dangerous")))
    (is (not (security/safe-path? "path/../../../etc/passwd")))
    (is (not (security/safe-path? "/absolute/path")))
    (is (not (security/safe-path? "C:\\windows\\system32")))
    (is (not (security/safe-path? "path\\with\\backslashes")))))

(deftest test-sanitize-path
  (testing "sanitize-path validation and normalization"
    (is (= "valid/path" (security/sanitize-path "valid/path")))
    (is (thrown? js/Error (security/sanitize-path "../dangerous")))
    (is (thrown? js/Error (security/sanitize-path "/absolute")))))

(deftest test-validate-file-name
  (testing "file name validation"
    (is (security/validate-file-name "valid-file.txt"))
    (is (security/validate-file-name "file_with_underscores.md"))
    (is (not (security/validate-file-name "")))
    (is (not (security/validate-file-name (apply str (repeat 300 "a")))))
    (is (not (security/validate-file-name "file\u0000with\u0001null")))))

(deftest test-sanitize-file-name
  (testing "file name sanitization"
    (is (= "valid-file.txt" (security/sanitize-file-name "valid-file.txt")))
    (is (thrown? js/Error (security/sanitize-file-name "")))
    (is (thrown? js/Error (security/sanitize-file-name "dangerous/../../../file")))))

(deftest test-validate-input-string
  (testing "input string validation"
    (is (security/validate-input-string "valid input" 100))
    (is (not (security/validate-input-string "too long input string that exceeds limit" 10)))
    (is (not (security/validate-input-string "input\u0000with\u0001null" 100)))))

(deftest test-sanitize-input-string
  (testing "input string sanitization"
    (is (= "valid input" (security/sanitize-input-string "  valid input  " 100)))
    (is (thrown? js/Error (security/sanitize-input-string "too long input string that exceeds limit" 10)))))

(deftest test-hash-sensitive-data
  (testing "sensitive data hashing"
    (let [data "sensitive information"
          hash1 (security/hash-sensitive-data data)
          hash2 (security/hash-sensitive-data data)]
      (is (= hash1 hash2)) ; Same input should produce same hash
      (is (not= hash1 (security/hash-sensitive-data "different data"))))))

(deftest test-validate-encryption-key
  (testing "encryption key validation"
    (is (security/validate-encryption-key "StrongP@ssw0rd123!"))
    (is (not (security/validate-encryption-key "weak")))
    (is (not (security/validate-encryption-key "password")))
    (is (not (security/validate-encryption-key "short")))))