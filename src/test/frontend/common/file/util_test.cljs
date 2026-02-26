(ns frontend.common.file.util-test
  (:require [cljs.test :refer [deftest is testing]]
            [frontend.common.file.util :as file-util]))

(deftest file-name-sanity-test
  (testing "Normal titles"
    (is (= "Hello World" (file-util/file-name-sanity "Hello World")))
    (is (= "My Page" (file-util/file-name-sanity "My Page"))))

  (testing "Reserved characters"
    (is (= "a%3Ab" (file-util/file-name-sanity "a:b")))
    (is (= "a%2Ab" (file-util/file-name-sanity "a*b")))
    (is (= "a%3Fb" (file-util/file-name-sanity "a?b")))
    (is (= "a%22b" (file-util/file-name-sanity "a\"b")))
    (is (= "a%3Cb" (file-util/file-name-sanity "a<b")))
    (is (= "a%3Eb" (file-util/file-name-sanity "a>b")))
    (is (= "a%7Cb" (file-util/file-name-sanity "a|b")))
    (is (= "a%23b" (file-util/file-name-sanity "a#b")))
    (is (= "a%5Cb" (file-util/file-name-sanity "a\\b"))))

  (testing "Slashes"
    (is (= "a___b" (file-util/file-name-sanity "a/b")))
    (is (= "a___b___c" (file-util/file-name-sanity "a/b/c")))
    (is (= "a___b" (file-util/file-name-sanity "/a/b/"))))

  (testing "Windows reserved filebodies"
    (is (= "CON___" (file-util/file-name-sanity "CON")))
    (is (= "PRN___" (file-util/file-name-sanity "PRN")))
    (is (= "AUX___" (file-util/file-name-sanity "AUX")))
    (is (= "NUL___" (file-util/file-name-sanity "NUL")))
    (is (= "COM1___" (file-util/file-name-sanity "COM1")))
    (is (= "LPT1___" (file-util/file-name-sanity "LPT1"))))

  (testing "Titles ending with dot"
    (is (= "test.___" (file-util/file-name-sanity "test."))))

  (testing "Existing URL encoding"
    (is (= "foo%2520bar" (file-util/file-name-sanity "foo%20bar"))))

  (testing "Triple lowbars and ambiguous lowbars"
    (is (= "a%5F%5F%5Fb" (file-util/file-name-sanity "a___b")))
    (is (= "a%5F___b" (file-util/file-name-sanity "a_/b")))
    (is (= "a___%5Fb" (file-util/file-name-sanity "a/_b")))))

(deftest include-reserved-chars?-test
  (testing "Strings without reserved characters"
    (is (not (file-util/include-reserved-chars? "Hello World")))
    (is (not (file-util/include-reserved-chars? "My_Page"))))

  (testing "Strings with reserved characters"
    (is (file-util/include-reserved-chars? "a:b"))
    (is (file-util/include-reserved-chars? "a*b"))
    (is (file-util/include-reserved-chars? "a?b"))
    (is (file-util/include-reserved-chars? "a\"b"))
    (is (file-util/include-reserved-chars? "a<b"))
    (is (file-util/include-reserved-chars? "a>b"))
    (is (file-util/include-reserved-chars? "a|b"))
    (is (file-util/include-reserved-chars? "a#b"))
    (is (file-util/include-reserved-chars? "a\\b"))))
